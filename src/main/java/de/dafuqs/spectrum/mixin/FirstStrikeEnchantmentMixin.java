package de.dafuqs.spectrum.mixin;

import de.dafuqs.spectrum.*;
import de.dafuqs.spectrum.helpers.*;
import de.dafuqs.spectrum.registries.*;
import net.minecraft.entity.*;
import net.minecraft.entity.damage.*;
import net.minecraft.entity.player.*;
import net.minecraft.item.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;

@Mixin({LivingEntity.class, PlayerEntity.class})
public abstract class FirstStrikeEnchantmentMixin {
	
	@ModifyVariable(method = "applyDamage(Lnet/minecraft/entity/damage/DamageSource;F)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
	public float applyAdditionalFirstStrikeEnchantmentDamage(float amount, DamageSource source) {
		LivingEntity target = (LivingEntity) (Object) this;
		
		if (source.getAttacker() instanceof LivingEntity livingAttacker) {
			if (amount != 0F && target.getHealth() == target.getMaxHealth()) {
				ItemStack mainHandStack = livingAttacker.getMainHandStack();
				int level = SpectrumEnchantmentHelper.getUsableLevel(SpectrumEnchantments.FIRST_STRIKE, mainHandStack, livingAttacker);
				if (level > 0) {
					float additionalDamage = getAdditionalFirstStrikeEnchantmentDamage(level);
					amount += additionalDamage;
				}
			}
		}
		return amount;
	}
	
	private float getAdditionalFirstStrikeEnchantmentDamage(int level) {
		return SpectrumCommon.CONFIG.FirstStrikeDamagePerLevel * level;
	}
	
}