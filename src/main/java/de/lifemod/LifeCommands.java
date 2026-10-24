package de.lifemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class LifeCommands {
    private static final Random RANDOM = new Random();
    private static final int MAX_EDUCATION = 50;
    private static final long REQUEST_TIMEOUT_MS = 60_000;

    private static final String[] GENDERS = {"maennlich", "weiblich", "divers"};
    private static final String[] FAMILIES = {"arm", "normal", "reich"};
    private static final String[] TALENTS = {"schlau", "musik", "glueck"};

    private static final SimpleCommandExceptionType NOT_BORN = new SimpleCommandExceptionType(
            Text.literal("§cDu wurdest noch nicht geboren! Nutze /life create <name> <geschlecht> <familie> <talent>"));

    // ---------- Anfragen (Freundschaft, Beziehung, Kind) ----------
    private enum ReqType { FRIEND, LOVE, CHILD }

    private record Request(ReqType type, UUID from, String fromName, String extra, long expires) { }

    /** Schlüssel = Empfänger der Anfrage. */
    private static final Map<UUID, Request> PENDING = new HashMap<>();

    private static SuggestionProvider<ServerCommandSource> suggest(String... options) {
        return (ctx, builder) -> CommandSource.suggestMatching(Arrays.stream(options), builder);
    }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(CommandManager.literal("life")
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .then(CommandManager.argument("geschlecht", StringArgumentType.word())
                                        .suggests(suggest(GENDERS))
                                        .then(CommandManager.argument("familie", StringArgumentType.word())
                                                .suggests(suggest(FAMILIES))
                                                .then(CommandManager.argument("talent", StringArgumentType.word())
                                                        .suggests(suggest(TALENTS))
                                                        .executes(LifeCommands::create))))))
                .then(CommandManager.literal("status").executes(LifeCommands::status))
                .then(CommandManager.literal("school").executes(LifeCommands::school))
                .then(CommandManager.literal("jobs").executes(LifeCommands::jobs))
                .then(CommandManager.literal("work").executes(LifeCommands::work))
                .then(CommandManager.literal("quit").executes(LifeCommands::quit))
                .then(CommandManager.literal("job")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> CommandSource.suggestMatching(
                                        Arrays.stream(Job.values()).map(j -> j.id), builder))
                                .executes(LifeCommands::apply)))
                .then(CommandManager.literal("friend")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(LifeCommands::friend)))
                .then(CommandManager.literal("unfriend")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(LifeCommands::unfriend)))
                .then(CommandManager.literal("friends").executes(LifeCommands::friends))
                .then(CommandManager.literal("love")
                        .then(CommandManager.argument("player", EntityArgumentType.player())
                                .executes(LifeCommands::love)))
                .then(CommandManager.literal("breakup").executes(LifeCommands::breakup))
                .then(CommandManager.literal("child")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(LifeCommands::child)))
                .then(CommandManager.literal("children").executes(LifeCommands::children))
                .then(CommandManager.literal("accept").executes(LifeCommands::accept))
                .then(CommandManager.literal("decline").executes(LifeCommands::decline)));
    }

    // ---------- Hilfsfunktionen ----------
    private static void msg(CommandContext<ServerCommandSource> ctx, String text) {
        ctx.getSource().sendFeedback(() -> Text.literal(text), false);
    }

    private static LifeState state(CommandContext<ServerCommandSource> ctx) {
        return LifeState.get(ctx.getSource().getServer());
    }

    private static PlayerLife life(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
        return state(ctx).getOrCreate(p.getUuid());
    }

    private static PlayerLife lifeReady(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = life(ctx);
        if (!l.created) throw NOT_BORN.create();
        return l;
    }

    private static boolean contains(String[] arr, String value) {
        for (String s : arr) if (s.equalsIgnoreCase(value)) return true;
        return false;
    }

    private static String nameOf(ServerPlayerEntity p) {
        return p.getName().getString();
    }

    // ---------- Geburt: Charakter erstellen ----------
    private static int create(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = life(ctx);
        if (l.created) { msg(ctx, "§cDu wurdest schon geboren!"); return 0; }

        String name = StringArgumentType.getString(ctx, "name");
        String gender = StringArgumentType.getString(ctx, "geschlecht").toLowerCase();
        String family = StringArgumentType.getString(ctx, "familie").toLowerCase();
        String talent = StringArgumentType.getString(ctx, "talent").toLowerCase();

        if (!contains(GENDERS, gender)) { msg(ctx, "§cGeschlecht: maennlich, weiblich oder divers"); return 0; }
        if (!contains(FAMILIES, family)) { msg(ctx, "§cFamilie: arm, normal oder reich"); return 0; }
        if (!contains(TALENTS, talent)) { msg(ctx, "§cTalent: schlau, musik oder glueck"); return 0; }

        l.created = true;
        l.name = name;
        l.gender = gender;
        l.family = family;
        l.talent = talent;
        l.age = 0;
        l.yearTicks = 0;

        switch (family) {
            case "arm" -> l.money = 20;
            case "reich" -> l.money = 2000;
            default -> l.money = 200;
        }
        switch (talent) {
            case "schlau" -> l.education = 5;
            case "musik" -> l.fame = 10;
            case "glueck" -> l.money += 300;
            default -> { }
        }

        state(ctx).markDirty();
        msg(ctx, "§aHerzlichen Glückwunsch, " + name + " wurde geboren! (" + family + "e Familie, Talent: " + talent + ")");
        msg(ctx, "§7Mit /life status siehst du dein Leben.");
        return 1;
    }

    // ---------- Status, Schule, Jobs ----------
    private static int status(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        Job j = l.getJob();
        msg(ctx, "§6--- " + l.name + " ---");
        msg(ctx, "Alter: " + l.age + " (" + l.stage() + ") | Geschlecht: " + l.gender);
        msg(ctx, "Familie: " + l.family + " | Talent: " + l.talent);
        msg(ctx, "Geld: " + l.money + " €");
        msg(ctx, "Bildung: " + l.education + "/" + MAX_EDUCATION);
        msg(ctx, "Beruf: " + (j == null ? "arbeitslos" : j.displayName));
        if (j != null && j.fameJob) msg(ctx, "Ruhm: " + l.fame);
        msg(ctx, "Partner: " + (l.hasPartner() ? l.partnerName : "keiner")
                + " | Kinder: " + l.children.size() + " | Freunde: " + l.friends.size());
        return 1;
    }

    private static int school(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        if (l.age < 6) { msg(ctx, "§cDu bist noch zu jung für die Schule."); return 0; }
        if (l.schoolCooldown > 0) {
            msg(ctx, "§cDu bist müde. Warte noch " + (l.schoolCooldown / 20) + " Sekunden.");
            return 0;
        }
        if (l.education >= MAX_EDUCATION) { msg(ctx, "§aDu hast schon die höchste Bildung!"); return 0; }
        l.education++;
        l.schoolCooldown = 1200;
        state(ctx).markDirty();
        msg(ctx, "§aDu hast gelernt! Bildung: " + l.education + "/" + MAX_EDUCATION);
        return 1;
    }

    private static int jobs(CommandContext<ServerCommandSource> ctx) {
        msg(ctx, "§6--- Jobs --- (/life job <name>)");
        for (Job j : Job.values()) {
            String extra = j.fameJob ? " [Ruhm-Job]" : "";
            msg(ctx, j.id + ": " + j.displayName + " | ab " + j.minAge + " J. | Bildung " + j.minEducation
                    + " | Grundgehalt " + j.basePay + " €" + extra);
        }
        return 1;
    }

    private static int apply(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        Job j = Job.byId(StringArgumentType.getString(ctx, "name"));
        if (j == null) { msg(ctx, "§cDen Job gibt es nicht. Siehe /life jobs"); return 0; }
        if (l.age < j.minAge) { msg(ctx, "§cDu bist zu jung. Mindestalter: " + j.minAge); return 0; }
        if (l.education < j.minEducation) {
            msg(ctx, "§cDir fehlt Bildung. Benötigt: " + j.minEducation + " (du: " + l.education + ")");
            return 0;
        }
        l.job = j.id;
        state(ctx).markDirty();
        msg(ctx, "§aDu arbeitest jetzt als " + j.displayName + "! Nutze /life work.");
        return 1;
    }

    private static int work(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        Job j = l.getJob();
        if (j == null) { msg(ctx, "§cDu hast keinen Job. Siehe /life jobs"); return 0; }
        if (l.workCooldown > 0) {
            msg(ctx, "§cNoch Pause. Warte " + (l.workCooldown / 20) + " Sekunden.");
            return 0;
        }
        long pay = j.basePay;
        if (j.fameJob) {
            pay += (long) l.fame * 2;
            int gain = 1 + RANDOM.nextInt(3);
            l.fame += gain;
            msg(ctx, "§dDu hast neue Fans gewonnen! Ruhm +" + gain);
        }
        l.money += pay;
        l.workCooldown = j.cooldownTicks;
        state(ctx).markDirty();
        msg(ctx, "§aDu hast gearbeitet und " + pay + " € verdient. Kontostand: " + l.money + " €");
        return 1;
    }

    private static int quit(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        l.job = "";
        state(ctx).markDirty();
        msg(ctx, "§eDu hast gekündigt.");
        return 1;
    }

    // ---------- Anfragen senden ----------
    private static void sendRequest(CommandContext<ServerCommandSource> ctx, ServerPlayerEntity me,
                                    ServerPlayerEntity target, ReqType type, String extra, String text) {
        PENDING.put(target.getUuid(), new Request(type, me.getUuid(), nameOf(me), extra,
                System.currentTimeMillis() + REQUEST_TIMEOUT_MS));
        target.sendMessage(Text.literal("§e" + nameOf(me) + " " + text), false);
        target.sendMessage(Text.literal("§aAnnehmen: /life accept  §7|  §cAblehnen: /life decline  §7(60 Sekunden)"), false);
        msg(ctx, "§aAnfrage an " + nameOf(target) + " gesendet.");
    }

    private static int friend(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        PlayerLife l = lifeReady(ctx);
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        if (target.getUuid().equals(me.getUuid())) { msg(ctx, "§cDu kannst nicht mit dir selbst befreundet sein."); return 0; }
        PlayerLife tl = state(ctx).getOrCreate(target.getUuid());
        if (!tl.created) { msg(ctx, "§c" + nameOf(target) + " wurde noch nicht geboren."); return 0; }
        if (l.isFriend(target.getUuid().toString())) { msg(ctx, "§cIhr seid schon befreundet."); return 0; }
        if (l.age < 6 || tl.age < 6) { msg(ctx, "§cZum Freunde finden seid ihr noch zu klein (ab 6 Jahren)."); return 0; }
        sendRequest(ctx, me, target, ReqType.FRIEND, "", "möchte mit dir befreundet sein.");
        return 1;
    }

    private static int unfriend(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        String uuid = l.removeFriendByName(StringArgumentType.getString(ctx, "name"));
        if (uuid == null) { msg(ctx, "§cDen Freund gibt es nicht. Siehe /life friends"); return 0; }
        state(ctx).getOrCreate(UUID.fromString(uuid)).removeFriendByUuid(me.getUuid().toString());
        msg(ctx, "§eFreundschaft beendet.");
        return 1;
    }

    private static int friends(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        if (l.friends.isEmpty()) { msg(ctx, "§7Du hast noch keine Freunde. Nutze /life friend <spieler>"); return 1; }
        msg(ctx, "§6--- Freunde ---");
        for (String f : l.friends) {
            String[] parts = f.split(";", 2);
            msg(ctx, "- " + (parts.length == 2 ? parts[1] : f));
        }
        return 1;
    }

    private static int love(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        PlayerLife l = lifeReady(ctx);
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "player");
        if (target.getUuid().equals(me.getUuid())) { msg(ctx, "§cDas geht nur mit einer anderen Person."); return 0; }
        PlayerLife tl = state(ctx).getOrCreate(target.getUuid());
        if (!tl.created) { msg(ctx, "§c" + nameOf(target) + " wurde noch nicht geboren."); return 0; }
        if (l.age < LifeMod.ADULT_AGE || tl.age < LifeMod.ADULT_AGE) {
            msg(ctx, "§cEine Beziehung ist erst ab " + LifeMod.ADULT_AGE + " Jahren möglich (für beide).");
            return 0;
        }
        if (l.hasPartner()) { msg(ctx, "§cDu hast schon einen Partner. Erst /life breakup."); return 0; }
        if (tl.hasPartner()) { msg(ctx, "§c" + nameOf(target) + " ist schon vergeben."); return 0; }
        sendRequest(ctx, me, target, ReqType.LOVE, "", "möchte mit dir zusammen sein.");
        return 1;
    }

    private static int breakup(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        PlayerLife l = lifeReady(ctx);
        if (!l.hasPartner()) { msg(ctx, "§cDu hast keinen Partner."); return 0; }
        UUID partnerId = UUID.fromString(l.partnerUuid);
        PlayerLife pl = state(ctx).getOrCreate(partnerId);
        pl.partnerUuid = "";
        pl.partnerName = "";
        l.partnerUuid = "";
        l.partnerName = "";
        state(ctx).markDirty();
        msg(ctx, "§eBeziehung beendet.");
        ServerPlayerEntity partner = ctx.getSource().getServer().getPlayerManager().getPlayer(partnerId);
        if (partner != null) partner.sendMessage(Text.literal("§e" + nameOf(me) + " hat die Beziehung beendet."), false);
        return 1;
    }

    private static int child(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        PlayerLife l = lifeReady(ctx);
        String childName = StringArgumentType.getString(ctx, "name");
        if (!l.hasPartner()) { msg(ctx, "§cFür ein Kind brauchst du einen Partner (/life love)."); return 0; }
        if (l.age < LifeMod.ADULT_AGE) { msg(ctx, "§cDu bist noch zu jung dafür."); return 0; }
        ServerPlayerEntity partner = ctx.getSource().getServer().getPlayerManager()
                .getPlayer(UUID.fromString(l.partnerUuid));
        if (partner == null) { msg(ctx, "§cDein Partner muss online sein."); return 0; }
        PlayerLife pl = state(ctx).getOrCreate(partner.getUuid());
        if (pl.age < LifeMod.ADULT_AGE) { msg(ctx, "§cDein Partner ist noch zu jung dafür."); return 0; }
        sendRequest(ctx, me, partner, ReqType.CHILD, childName,
                "möchte mit dir ein Kind bekommen: " + childName + " (kostet jeden von euch "
                        + LifeMod.CHILD_COST_PER_YEAR + " € pro Lebensjahr).");
        return 1;
    }

    private static int children(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        PlayerLife l = lifeReady(ctx);
        if (l.children.isEmpty()) { msg(ctx, "§7Du hast keine Kinder."); return 1; }
        msg(ctx, "§6--- Kinder ---");
        for (PlayerLife.Child c : l.children) msg(ctx, "- " + c.name + " (" + c.age + " Jahre)");
        return 1;
    }

    // ---------- Anfragen beantworten ----------
    private static int decline(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        Request r = PENDING.remove(me.getUuid());
        if (r == null) { msg(ctx, "§cKeine offene Anfrage."); return 0; }
        msg(ctx, "§eAnfrage abgelehnt.");
        ServerPlayerEntity from = ctx.getSource().getServer().getPlayerManager().getPlayer(r.from());
        if (from != null) from.sendMessage(Text.literal("§e" + nameOf(me) + " hat deine Anfrage abgelehnt."), false);
        return 1;
    }

    private static int accept(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity me = ctx.getSource().getPlayerOrThrow();
        PlayerLife l = lifeReady(ctx);
        Request r = PENDING.remove(me.getUuid());
        if (r == null || System.currentTimeMillis() > r.expires()) {
            msg(ctx, "§cKeine offene Anfrage (oder sie ist abgelaufen).");
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        ServerPlayerEntity from = server.getPlayerManager().getPlayer(r.from());
        if (from == null) { msg(ctx, "§c" + r.fromName() + " ist nicht mehr online."); return 0; }
        PlayerLife fl = state(ctx).getOrCreate(from.getUuid());

        switch (r.type()) {
            case FRIEND -> {
                l.addFriend(from.getUuid().toString(), nameOf(from));
                fl.addFriend(me.getUuid().toString(), nameOf(me));
                msg(ctx, "§aIhr seid jetzt Freunde!");
                from.sendMessage(Text.literal("§a" + nameOf(me) + " ist jetzt dein Freund!"), false);
            }
            case LOVE -> {
                if (l.hasPartner() || fl.hasPartner()) { msg(ctx, "§cEiner von euch hat schon einen Partner."); return 0; }
                l.partnerUuid = from.getUuid().toString();
                l.partnerName = nameOf(from);
                fl.partnerUuid = me.getUuid().toString();
                fl.partnerName = nameOf(me);
                msg(ctx, "§dIhr seid jetzt zusammen!");
                from.sendMessage(Text.literal("§d" + nameOf(me) + " ist jetzt dein Partner!"), false);
            }
            case CHILD -> {
                if (!l.hasPartner() || !l.partnerUuid.equals(from.getUuid().toString())) {
                    msg(ctx, "§cIhr seid kein Paar mehr.");
                    return 0;
                }
                l.children.add(new PlayerLife.Child(r.extra(), 0));
                fl.children.add(new PlayerLife.Child(r.extra(), 0));
                msg(ctx, "§dHerzlichen Glückwunsch! " + r.extra() + " wurde geboren!");
                from.sendMessage(Text.literal("§dHerzlichen Glückwunsch! " + r.extra() + " wurde geboren!"), false);
            }
        }
        state(ctx).markDirty();
        return 1;
    }
}
