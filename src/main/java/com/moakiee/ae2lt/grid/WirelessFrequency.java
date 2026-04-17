package com.moakiee.ae2lt.grid;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Represents a wireless frequency that pairs controllers and receivers.
 */
public class WirelessFrequency {

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_PASSWORD_LENGTH = 16;

    public static final String TAG_ID = "id";
    public static final String TAG_NAME = "name";
    public static final String TAG_COLOR = "color";
    public static final String TAG_OWNER = "owner";
    public static final String TAG_SECURITY = "security";
    public static final String TAG_PASSWORD = "password";
    public static final String TAG_MEMBERS = "members";

    /** NBT sync types */
    public static final byte NBT_BASIC = 0;
    public static final byte NBT_SAVE_ALL = 1;
    public static final byte NBT_MEMBERS_ONLY = 2;

    private int id;
    private String name;
    private int color;
    private UUID ownerUUID;
    private FrequencySecurityLevel security;
    private String password;
    private final Map<UUID, FrequencyMember> members = new HashMap<>();

    public WirelessFrequency() {
        this(-1, "", 0xFFFFFF, new UUID(0, 0), FrequencySecurityLevel.PUBLIC, "");
    }

    public WirelessFrequency(int id, String name, int color,
                             @Nonnull UUID ownerUUID,
                             @Nonnull FrequencySecurityLevel security,
                             @Nonnull String password) {
        this.id = id;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.ownerUUID = ownerUUID;
        this.security = security;
        this.password = password;
    }

    public WirelessFrequency(int id, String name, int color,
                             @Nonnull Player owner,
                             @Nonnull FrequencySecurityLevel security,
                             @Nonnull String password) {
        this(id, name, color, owner.getUUID(), security, password);
        members.put(ownerUUID, FrequencyMember.create(owner, FrequencyAccessLevel.OWNER));
    }

    // ── Getters / Setters ──

    public int getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public boolean setName(@Nonnull String name) {
        if (!name.equals(this.name) && !name.isBlank() && name.length() <= MAX_NAME_LENGTH) {
            this.name = name;
            return true;
        }
        return false;
    }

    public int getColor() {
        return color;
    }

    public boolean setColor(int color) {
        color &= 0xFFFFFF;
        if (this.color != color) {
            this.color = color;
            return true;
        }
        return false;
    }

    @Nonnull
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nonnull
    public FrequencySecurityLevel getSecurity() {
        return security;
    }

    public boolean setSecurity(@Nonnull FrequencySecurityLevel security) {
        if (this.security != security) {
            this.security = security;
            return true;
        }
        return false;
    }

    @Nonnull
    public String getPassword() {
        return password;
    }

    public void setPassword(@Nonnull String password) {
        this.password = password;
    }

    // ── Members ──

    @Nonnull
    public FrequencyAccessLevel getPlayerAccess(@Nonnull Player player) {
        UUID uuid = player.getUUID();
        if (ownerUUID.equals(uuid)) {
            return FrequencyAccessLevel.OWNER;
        }
        FrequencyMember member = members.get(uuid);
        if (member != null) {
            return member.getAccessLevel();
        }
        return security == FrequencySecurityLevel.PUBLIC ? FrequencyAccessLevel.USER : FrequencyAccessLevel.BLOCKED;
    }

    public boolean canPlayerAccess(@Nonnull Player player, @Nonnull String password) {
        FrequencyAccessLevel access = getPlayerAccess(player);
        if (access.canUse()) {
            return true;
        }
        // Encrypted frequencies allow temporary access with the correct password.
        if (security == FrequencySecurityLevel.ENCRYPTED && !password.isEmpty()) {
            return password.equals(this.password);
        }
        return false;
    }

    public int changeMembership(@Nonnull Player actor, @Nonnull UUID targetUUID, byte type) {
        FrequencyAccessLevel actorAccess = getPlayerAccess(actor);
        if (!actorAccess.canEdit()) {
            return RESPONSE_NO_PERMISSION;
        }

        boolean self = actor.getUUID().equals(targetUUID);
        FrequencyMember current = members.get(targetUUID);

        switch (type) {
            case MEMBERSHIP_SET_USER -> {
                if (current == null) {
                    var server = actor.level().getServer();
                    if (server == null) {
                        return RESPONSE_INVALID_USER;
                    }

                    Player target = server.getPlayerList().getPlayer(targetUUID);
                    if (target == null) {
                        return RESPONSE_INVALID_USER;
                    }

                    members.put(targetUUID, FrequencyMember.create(target, FrequencyAccessLevel.USER));
                    return RESPONSE_SUCCESS;
                }
                if (current.getAccessLevel().canDelete()) {
                    return RESPONSE_INVALID_USER;
                }
                return current.setAccessLevel(FrequencyAccessLevel.USER) ? RESPONSE_SUCCESS : RESPONSE_INVALID_USER;
            }
            case MEMBERSHIP_SET_ADMIN -> {
                if (!actorAccess.canDelete()) return RESPONSE_NO_PERMISSION;
                if (current == null) {
                    var server = actor.level().getServer();
                    if (server == null) {
                        return RESPONSE_INVALID_USER;
                    }

                    Player target = server.getPlayerList().getPlayer(targetUUID);
                    if (target == null) {
                        return RESPONSE_INVALID_USER;
                    }

                    members.put(targetUUID, FrequencyMember.create(target, FrequencyAccessLevel.ADMIN));
                    return RESPONSE_SUCCESS;
                }
                if (current.getAccessLevel().canDelete()) return RESPONSE_INVALID_USER;
                return current.setAccessLevel(FrequencyAccessLevel.ADMIN) ? RESPONSE_SUCCESS : RESPONSE_INVALID_USER;
            }
            case MEMBERSHIP_CANCEL -> {
                if (current != null && !current.getAccessLevel().canDelete()) {
                    members.remove(targetUUID);
                    return RESPONSE_SUCCESS;
                }
                return RESPONSE_INVALID_USER;
            }
            case MEMBERSHIP_TRANSFER_OWNERSHIP -> {
                if (!actorAccess.canDelete()) return RESPONSE_NO_PERMISSION;
                if (self) return RESPONSE_INVALID_USER;

                var server = actor.level().getServer();
                if (server == null) {
                    return RESPONSE_INVALID_USER;
                }

                Player target = current == null ? server.getPlayerList().getPlayer(targetUUID) : null;
                if (current == null && target == null) {
                    return RESPONSE_INVALID_USER;
                }

                // Demote the old owner before assigning the new one.
                for (var m : members.values()) {
                    if (m.getAccessLevel().canDelete()) {
                        m.setAccessLevel(FrequencyAccessLevel.USER);
                    }
                }

                if (current != null) {
                    current.setAccessLevel(FrequencyAccessLevel.OWNER);
                } else {
                    members.put(targetUUID, FrequencyMember.create(target, FrequencyAccessLevel.OWNER));
                }
                ownerUUID = targetUUID;
                return RESPONSE_SUCCESS;
            }
        }
        return RESPONSE_INVALID_USER;
    }

    // ── Membership operation constants ──

    public static final byte MEMBERSHIP_SET_USER = 0;
    public static final byte MEMBERSHIP_SET_ADMIN = 1;
    public static final byte MEMBERSHIP_CANCEL = 2;
    public static final byte MEMBERSHIP_TRANSFER_OWNERSHIP = 3;

    // ── Response codes ──

    public static final int RESPONSE_SUCCESS = 0;
    public static final int RESPONSE_NO_PERMISSION = 1;
    public static final int RESPONSE_INVALID_USER = 2;

    // ── NBT ──

    public void writeToTag(@Nonnull CompoundTag tag, byte type) {
        if (type == NBT_BASIC || type == NBT_SAVE_ALL) {
            tag.putInt(TAG_ID, id);
            tag.putString(TAG_NAME, name);
            tag.putInt(TAG_COLOR, color);
            tag.putUUID(TAG_OWNER, ownerUUID);
            tag.putByte(TAG_SECURITY, security.getId());
        }
        if (type == NBT_SAVE_ALL) {
            tag.putString(TAG_PASSWORD, password);
            if (!members.isEmpty()) {
                ListTag list = new ListTag();
                for (FrequencyMember m : members.values()) {
                    CompoundTag sub = new CompoundTag();
                    m.writeNBT(sub);
                    list.add(sub);
                }
                tag.put(TAG_MEMBERS, list);
            }
        }
        if (type == NBT_MEMBERS_ONLY) {
            tag.putInt(TAG_ID, id);
            ListTag list = new ListTag();
            for (FrequencyMember m : members.values()) {
                CompoundTag sub = new CompoundTag();
                m.writeNBT(sub);
                list.add(sub);
            }
            tag.put(TAG_MEMBERS, list);
        }
    }

    public void readFromTag(@Nonnull CompoundTag tag, byte type) {
        if (type == NBT_BASIC || type == NBT_SAVE_ALL) {
            id = tag.getInt(TAG_ID);
            name = tag.getString(TAG_NAME);
            color = tag.getInt(TAG_COLOR);
            ownerUUID = tag.getUUID(TAG_OWNER);
            security = FrequencySecurityLevel.fromId(tag.getByte(TAG_SECURITY));
        }
        if (type == NBT_SAVE_ALL) {
            password = tag.getString(TAG_PASSWORD);
            members.clear();
            ListTag list = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FrequencyMember m = new FrequencyMember(list.getCompound(i));
                members.put(m.getPlayerUUID(), m);
            }
        }
        if (type == NBT_MEMBERS_ONLY) {
            members.clear();
            ListTag list = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FrequencyMember m = new FrequencyMember(list.getCompound(i));
                members.put(m.getPlayerUUID(), m);
            }
        }
    }

    @Override
    public String toString() {
        return "WirelessFrequency{id=" + id + ", name='" + name + "', owner=" + ownerUUID + '}';
    }
}
