package com.chicoestudioso.caricraft.server.challenge;

import com.chicoestudioso.caricraft.CaricraftConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
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
 * Equipa y refuerza a los mobs del Desafío Dúo. La escala no tiene techo fijo: cada nivel suma vida, daño,
 * velocidad y resistencia, y el equipo pasa de cota de malla a hierro, diamante y netherita con encantamientos
 * y efectos cada vez mayores. Ver la tabla en docs/CHALLENGE.md.
 */
public final class ChallengeGear {
    private static final float DROP_CHANCE = 0.04f;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private enum Tier { CHAIN, IRON, DIAMOND, NETHERITE }

    private ChallengeGear() {
    }

    /**
     * @param level nivel efectivo (grupo + oleada, +3 si es élite), desde 1
     */
    public static void apply(Mob mob, int level, RandomSource random) {
        level = Math.max(1, level);
        mob.setCanPickUpLoot(false);
        modifier(mob, Attributes.MAX_HEALTH, "challenge_health", 0.12 * level);
        modifier(mob, Attributes.ATTACK_DAMAGE, "challenge_damage", 0.08 * level);
        modifier(mob, Attributes.FOLLOW_RANGE, "challenge_follow", Math.min(1.5, 0.5 + 0.05 * level));
        modifier(mob, Attributes.MOVEMENT_SPEED, "challenge_speed", Math.min(0.25, 0.02 * level));
        modifier(mob, Attributes.KNOCKBACK_RESISTANCE, "challenge_knockback", Math.min(0.6, 0.03 * level));

        EntityType<?> type = mob.getType();
        boolean zombie = type == EntityType.ZOMBIE || type == EntityType.HUSK || type == EntityType.DROWNED;
        boolean skeleton = type == EntityType.SKELETON || type == EntityType.STRAY;
        if (zombie) {
            modifier(mob, Attributes.SPAWN_REINFORCEMENTS_CHANCE, "challenge_reinforcements", Math.min(0.5, 0.02 * level));
            armor(mob, level, random);
            if (type == EntityType.DROWNED && random.nextFloat() < 0.25f) {
                ItemStack trident = new ItemStack(Items.TRIDENT);
                if (level >= 6) {
                    enchant(mob, trident, Enchantments.IMPALING, Math.min(5, (level - 4) / 2));
                }
                equip(mob, EquipmentSlot.MAINHAND, trident);
            } else {
                equip(mob, EquipmentSlot.MAINHAND, meleeWeapon(mob, level, random));
            }
        } else if (skeleton) {
            armor(mob, level, random);
            equip(mob, EquipmentSlot.MAINHAND, bow(mob, level));
        }

        // Efectos permanentes. Las arañas los reciben antes (no llevan equipo).
        int effectLevel = (type == EntityType.SPIDER || type == EntityType.CAVE_SPIDER) ? level + 2 : level;
        effect(mob, MobEffects.MOVEMENT_SPEED, effectLevel, 6, 14);
        effect(mob, MobEffects.DAMAGE_BOOST, effectLevel, 8, 16);
        effect(mob, MobEffects.DAMAGE_RESISTANCE, effectLevel, 10, 20);
        effect(mob, MobEffects.FIRE_RESISTANCE, effectLevel, 12, Integer.MAX_VALUE);
        effect(mob, MobEffects.REGENERATION, effectLevel, 18, 26);
        mob.setHealth(mob.getMaxHealth());
    }

    /** Marca visual de élite: nombre visible y brillo. */
    public static void markElite(Mob mob) {
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        mob.setCustomName(net.minecraft.network.chat.Component.translatable("caricraft.challenge.elite", mob.getType().getDescription())
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD));
        mob.setCustomNameVisible(true);
    }

    private static void effect(Mob mob, Holder<MobEffect> effect, int level, int firstLevel, int secondLevel) {
        if (level >= firstLevel) {
            int amplifier = level >= secondLevel ? 1 : 0;
            mob.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false));
        }
    }

    private static Tier tier(int level, RandomSource random) {
        if (level >= 20) {
            return Tier.NETHERITE;
        }
        if (level >= 15) {
            return random.nextFloat() < (level - 14) * 0.15f ? Tier.NETHERITE : Tier.DIAMOND;
        }
        if (level >= 10) {
            return Tier.DIAMOND;
        }
        if (level >= 6) {
            return random.nextFloat() < (level - 5) * 0.2f ? Tier.DIAMOND : Tier.IRON;
        }
        return level >= 3 ? Tier.IRON : Tier.CHAIN;
    }

    private static void armor(Mob mob, int level, RandomSource random) {
        float chance = Math.min(1.0f, 0.25f + 0.15f * level);
        for (EquipmentSlot slot : ARMOR) {
            if (random.nextFloat() >= chance) {
                continue;
            }
            ItemStack piece = new ItemStack(armorPiece(slot, tier(level, random)));
            if (level >= 4) {
                enchant(mob, piece, Enchantments.PROTECTION, Math.min(4, (level - 2) / 2));
            }
            if (level >= 12) {
                enchant(mob, piece, Enchantments.THORNS, Math.min(3, (level - 8) / 4));
            }
            equip(mob, slot, piece);
        }
    }

    private static Item armorPiece(EquipmentSlot slot, Tier tier) {
        return switch (slot) {
            case HEAD -> switch (tier) {
                case CHAIN -> Items.CHAINMAIL_HELMET;
                case IRON -> Items.IRON_HELMET;
                case DIAMOND -> Items.DIAMOND_HELMET;
                case NETHERITE -> Items.NETHERITE_HELMET;
            };
            case CHEST -> switch (tier) {
                case CHAIN -> Items.CHAINMAIL_CHESTPLATE;
                case IRON -> Items.IRON_CHESTPLATE;
                case DIAMOND -> Items.DIAMOND_CHESTPLATE;
                case NETHERITE -> Items.NETHERITE_CHESTPLATE;
            };
            case LEGS -> switch (tier) {
                case CHAIN -> Items.CHAINMAIL_LEGGINGS;
                case IRON -> Items.IRON_LEGGINGS;
                case DIAMOND -> Items.DIAMOND_LEGGINGS;
                case NETHERITE -> Items.NETHERITE_LEGGINGS;
            };
            default -> switch (tier) {
                case CHAIN -> Items.CHAINMAIL_BOOTS;
                case IRON -> Items.IRON_BOOTS;
                case DIAMOND -> Items.DIAMOND_BOOTS;
                case NETHERITE -> Items.NETHERITE_BOOTS;
            };
        };
    }

    private static ItemStack meleeWeapon(Mob mob, int level, RandomSource random) {
        Item item;
        if (level >= 20) {
            item = random.nextBoolean() ? Items.NETHERITE_SWORD : Items.NETHERITE_AXE;
        } else if (level >= 15) {
            item = random.nextBoolean() ? Items.NETHERITE_SWORD : Items.DIAMOND_AXE;
        } else if (level >= 10) {
            item = Items.DIAMOND_AXE;
        } else if (level >= 6) {
            item = Items.DIAMOND_SWORD;
        } else {
            item = switch (level) {
                case 1 -> Items.STONE_SWORD;
                case 2 -> Items.IRON_SWORD;
                case 3 -> Items.IRON_AXE;
                default -> Items.IRON_SWORD;
            };
        }
        ItemStack weapon = new ItemStack(item);
        if (level >= 4) {
            enchant(mob, weapon, Enchantments.SHARPNESS, Math.min(5, (level - 2) / 2));
        }
        if (level >= 8) {
            enchant(mob, weapon, Enchantments.FIRE_ASPECT, Math.min(2, (level - 4) / 4));
        }
        if (level >= 12) {
            enchant(mob, weapon, Enchantments.KNOCKBACK, 1);
        }
        return weapon;
    }

    private static ItemStack bow(Mob mob, int level) {
        ItemStack bow = new ItemStack(Items.BOW);
        if (level >= 2) {
            enchant(mob, bow, Enchantments.POWER, Math.min(5, level - 1));
        }
        if (level >= 5) {
            enchant(mob, bow, Enchantments.PUNCH, Math.min(2, (level - 2) / 3));
        }
        if (level >= 9) {
            enchant(mob, bow, Enchantments.FLAME, 1);
        }
        return bow;
    }

    private static void equip(Mob mob, EquipmentSlot slot, ItemStack stack) {
        mob.setItemSlot(slot, stack);
        mob.setDropChance(slot, DROP_CHANCE);
    }

    private static void enchant(Mob mob, ItemStack stack, ResourceKey<Enchantment> key, int level) {
        if (level <= 0) {
            return;
        }
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
