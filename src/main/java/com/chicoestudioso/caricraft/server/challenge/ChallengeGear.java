package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.CaricraftConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Equipa y refuerza a los mobs del Desafío Dúo según el nivel (1 = suave, 5 = muy duro).
 * Las piezas casi nunca caen al morir, para no convertir el desafío en una fuente de armaduras.
 */
public final class ChallengeGear {
    private static final float DROP_CHANCE = 0.04f;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private ChallengeGear() {
    }

    public static void apply(Mob mob, int level, RandomSource random) {
        level = Math.max(1, Math.min(5, level));
        mob.setCanPickUpLoot(false);
        modifier(mob, Attributes.MAX_HEALTH, "challenge_health", 0.15 * level);
        modifier(mob, Attributes.ATTACK_DAMAGE, "challenge_damage", 0.10 * level);
        modifier(mob, Attributes.FOLLOW_RANGE, "challenge_follow", 0.5);
        modifier(mob, Attributes.MOVEMENT_SPEED, "challenge_speed", Math.min(0.15, 0.03 * level));
        mob.setHealth(mob.getMaxHealth());

        EntityType<?> type = mob.getType();
        if (type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED) {
            armor(mob, level, random);
            if (type == EntityType.DROWNED && random.nextFloat() < 0.25f) {
                equip(mob, EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
            } else {
                equip(mob, EquipmentSlot.MAINHAND, meleeWeapon(mob, level));
            }
        } else if (type == EntityType.SKELETON || type == EntityType.STRAY) {
            armor(mob, level, random);
            ItemStack bow = new ItemStack(Items.BOW);
            if (level >= 2) {
                enchant(mob, bow, Enchantments.POWER, level - 1);
            }
            if (level >= 5) {
                enchant(mob, bow, Enchantments.PUNCH, 1);
            }
            equip(mob, EquipmentSlot.MAINHAND, bow);
        } else if (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) {
            if (level >= 2) {
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, 0));
            }
            if (level >= 4) {
                mob.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, MobEffectInstance.INFINITE_DURATION, 0));
            }
        }
    }

    private static void armor(Mob mob, int level, RandomSource random) {
        float chance = Math.min(0.95f, 0.25f + 0.15f * level);
        for (EquipmentSlot slot : ARMOR) {
            if (random.nextFloat() < chance) {
                ItemStack piece = new ItemStack(armorPiece(slot, level, random));
                if (level >= 4) {
                    enchant(mob, piece, Enchantments.PROTECTION, level - 3);
                }
                equip(mob, slot, piece);
            }
        }
    }

    private static Item armorPiece(EquipmentSlot slot, int level, RandomSource random) {
        boolean diamond = level >= 5 && random.nextFloat() < 0.35f;
        boolean iron = level >= 3;
        return switch (slot) {
            case HEAD -> diamond ? Items.DIAMOND_HELMET : iron ? Items.IRON_HELMET : Items.CHAINMAIL_HELMET;
            case CHEST -> diamond ? Items.DIAMOND_CHESTPLATE : iron ? Items.IRON_CHESTPLATE : Items.CHAINMAIL_CHESTPLATE;
            case LEGS -> diamond ? Items.DIAMOND_LEGGINGS : iron ? Items.IRON_LEGGINGS : Items.CHAINMAIL_LEGGINGS;
            default -> diamond ? Items.DIAMOND_BOOTS : iron ? Items.IRON_BOOTS : Items.CHAINMAIL_BOOTS;
        };
    }

    private static ItemStack meleeWeapon(Mob mob, int level) {
        ItemStack weapon = new ItemStack(switch (level) {
            case 1 -> Items.STONE_SWORD;
            case 2 -> Items.IRON_SWORD;
            case 3 -> Items.IRON_AXE;
            case 4 -> Items.IRON_SWORD;
            default -> Items.DIAMOND_SWORD;
        });
        if (level >= 4) {
            enchant(mob, weapon, Enchantments.SHARPNESS, level - 3);
        }
        return weapon;
    }

    private static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, DROP_CHANCE);
    }

    private static void enchant(Mob mob, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        mob.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key)
                .ifPresent(holder -> stack.enchant(holder, level));
    }

    private static void modifier(Mob mob, Holder<Attribute> attribute, String name, double amount) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && !instance.hasModifier(CaricraftConstants.id(name))) {
            instance.addPermanentModifier(new AttributeModifier(CaricraftConstants.id(name), amount,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }
}
