package com.sammy.catskincRemake.client;

import com.sammy.catskincRemake.CatskincRemake;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class ModSounds {
    private static final String REGISTRY_MOD_ID = detectRegistryModId();
    private static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(REGISTRY_MOD_ID, RegistryKeys.SOUND_EVENT);

    public static final Identifier ID_UI_UPLOAD = Identifiers.mod("ui.upload");
    public static final Identifier ID_UI_ERROR = Identifiers.mod("ui.error");
    public static final Identifier ID_UI_COMPLETE = Identifiers.mod("ui.complete");

    public static final RegistrySupplier<SoundEvent> UI_UPLOAD =
            SOUND_EVENTS.register(ID_UI_UPLOAD, () -> SoundEvent.of(ID_UI_UPLOAD));
    public static final RegistrySupplier<SoundEvent> UI_ERROR =
            SOUND_EVENTS.register(ID_UI_ERROR, () -> SoundEvent.of(ID_UI_ERROR));
    public static final RegistrySupplier<SoundEvent> UI_COMPLETE =
            SOUND_EVENTS.register(ID_UI_COMPLETE, () -> SoundEvent.of(ID_UI_COMPLETE));

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
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, 1.0F));
        } catch (Exception ignored) {
        }
    }

    public static void playClick() {
        try {
            MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK, 1.0F));
        } catch (Exception ignored) {
        }
    }

    private static String detectRegistryModId() {
        try {
            Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            return "catskinc_remake";
        } catch (ClassNotFoundException ignored) {
            return CatskincRemake.MOD_ID;
        }
    }
}

