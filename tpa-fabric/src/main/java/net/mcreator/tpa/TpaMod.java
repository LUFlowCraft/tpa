package net.mcreator.tpa;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TpaMod implements ModInitializer {
    public static final String MOD_ID = "tpa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        TpaManager.TPA.load();
        TpaManager.HERE.load();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            TpaCommands.register(dispatcher, "tpa", TpaManager.TPA);
            TpaCommands.register(dispatcher, "tpahere", TpaManager.HERE);
        });

        // entspricht dem 20-Tick-Timer (1 Sekunde) des Paper-Plugins
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                TpaManager.TPA.tick(server);
                TpaManager.HERE.tick(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TpaManager.TPA.save();
            TpaManager.HERE.save();
        });

        LOGGER.info("[TPA] timer gestartet!");
    }
}
