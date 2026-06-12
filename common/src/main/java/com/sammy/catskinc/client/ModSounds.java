package com.sammy.catskinc.client;

import com.sammy.catskinc.Catskinc;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    private static final String REGISTRY_MOD_ID = detectRegistryModId();
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(REGISTRY_MOD_ID, Registries.SOUND_EVENT);

    public static final ResourceLocation ID_UI_UPLOAD = Identifiers.mod("ui.upload");
    public static final ResourceLocation ID_UI_ERROR = Identifiers.mod("ui.error");
    public static final ResourceLocation ID_UI_COMPLETE = Identifiers.mod("ui.complete");

    public static final RegistrySupplier<SoundEvent> UI_UPLOAD =
            SOUND_EVENTS.register(ID_UI_UPLOAD, () -> SoundEvent.createVariableRangeEvent(ID_UI_UPLOAD));
    public static final RegistrySupplier<SoundEvent> UI_ERROR =
            SOUND_EVENTS.register(ID_UI_ERROR, () -> SoundEvent.createVariableRangeEvent(ID_UI_ERROR));
    public static final RegistrySupplier<SoundEvent> UI_COMPLETE =
            SOUND_EVENTS.register(ID_UI_COMPLETE, () -> SoundEvent.createVariableRangeEvent(ID_UI_COMPLETE));

    private static boolean registered;

    private ModSounds() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        SOUND_EVENTS.register();
    }

    public static void play(RegistrySupplier<SoundEvent> event) {
        try {
            SoundEvent sound = event.get();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
        } catch (Exception ignored) {
        }
    }

    public static void playClick() {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        } catch (Exception ignored) {
        }
    }

    private static String detectRegistryModId() {
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader");
            return "catskinc";
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            return "catskinc";
        } catch (ClassNotFoundException ignored) {
            return Catskinc.MOD_ID;
        }
    }
}


