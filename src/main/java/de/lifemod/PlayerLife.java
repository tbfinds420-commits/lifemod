package de.lifemod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;

import java.util.ArrayList;
import java.util.List;

public class PlayerLife {
    public static class Child {
        public String name;
        public int age;

        public Child(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    // Charakter (wird bei der Geburt gewählt)
    public boolean created = false;
    public String name = "";
    public String gender = "";
    public String family = "";
    public String talent = "";

    // Leben
    public int age = 0;
    public int yearTicks = 0;
    public long money = 0;
    public int education = 0;
    public int fame = 0;
    public String job = "";
    public int schoolCooldown = 0;
    public int workCooldown = 0;

    // Beziehungen
    public final List<String> friends = new ArrayList<>(); // Einträge: "uuid;name"
    public String partnerUuid = "";
    public String partnerName = "";
    public final List<Child> children = new ArrayList<>();

    public Job getJob() {
        return job.isEmpty() ? null : Job.byId(job);
    }

    public String stage() {
        if (age < 6) return "Baby/Kleinkind";
        if (age < 18) return "Schulkind/Jugendlicher";
        return "Erwachsener";
    }

    public boolean hasPartner() {
        return !partnerUuid.isEmpty();
    }

    public boolean isFriend(String uuid) {
        for (String f : friends) {
            if (f.startsWith(uuid + ";")) return true;
        }
        return false;
    }

    public void addFriend(String uuid, String friendName) {
        if (!isFriend(uuid)) friends.add(uuid + ";" + friendName);
    }

    /** Entfernt einen Freund per Namen und gibt dessen UUID zurück (oder null). */
    public String removeFriendByName(String friendName) {
        for (int i = 0; i < friends.size(); i++) {
            String[] parts = friends.get(i).split(";", 2);
            if (parts.length == 2 && parts[1].equalsIgnoreCase(friendName)) {
                friends.remove(i);
                return parts[0];
            }
        }
        return null;
    }

    public void removeFriendByUuid(String uuid) {
        friends.removeIf(f -> f.startsWith(uuid + ";"));
    }

    /** Lässt alle Kinder altern. Gibt die Namen der Kinder zurück, die erwachsen geworden sind. */
    public List<String> ageChildren(int years) {
        List<String> grown = new ArrayList<>();
        for (Child c : new ArrayList<>(children)) {
            c.age += years;
            if (c.age >= 18) {
                grown.add(c.name);
                children.remove(c);
            }
        }
        return grown;
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putBoolean("created", created);
        n.putString("name", name);
        n.putString("gender", gender);
        n.putString("family", family);
        n.putString("talent", talent);
        n.putInt("age", age);
        n.putInt("yearTicks", yearTicks);
        n.putLong("money", money);
        n.putInt("education", education);
        n.putInt("fame", fame);
        n.putString("job", job);
        n.putInt("schoolCooldown", schoolCooldown);
        n.putInt("workCooldown", workCooldown);
        n.putString("partnerUuid", partnerUuid);
        n.putString("partnerName", partnerName);

        NbtList fl = new NbtList();
        for (String f : friends) fl.add(NbtString.of(f));
        n.put("friends", fl);

        NbtList cl = new NbtList();
        for (Child c : children) {
            NbtCompound cn = new NbtCompound();
            cn.putString("name", c.name);
            cn.putInt("age", c.age);
            cl.add(cn);
        }
        n.put("children", cl);
        return n;
    }

    public static PlayerLife fromNbt(NbtCompound n) {
        PlayerLife p = new PlayerLife();
        p.created = n.getBoolean("created");
        p.name = n.getString("name");
        p.gender = n.getString("gender");
        p.family = n.getString("family");
        p.talent = n.getString("talent");
        p.age = n.getInt("age");
        p.yearTicks = n.getInt("yearTicks");
        p.money = n.getLong("money");
        p.education = n.getInt("education");
        p.fame = n.getInt("fame");
        p.job = n.getString("job");
        p.schoolCooldown = n.getInt("schoolCooldown");
        p.workCooldown = n.getInt("workCooldown");
        p.partnerUuid = n.getString("partnerUuid");
        p.partnerName = n.getString("partnerName");

        NbtList fl = n.getList("friends", NbtElement.STRING_TYPE);
        for (int i = 0; i < fl.size(); i++) p.friends.add(fl.getString(i));

        NbtList cl = n.getList("children", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < cl.size(); i++) {
            NbtCompound cn = cl.getCompound(i);
            p.children.add(new Child(cn.getString("name"), cn.getInt("age")));
        }
        return p;
    }
}
