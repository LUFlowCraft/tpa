package net.mcreator.tpa;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Eine Instanz pro Modus: TPA (Sender -> Ziel) und TPAHERE (Ziel -> Sender).
 * Entspricht den Paper-Prozeduren Send/Accept/Refuse/Enable/Disable/Timer.
 */
public final class TpaManager {
    public static final TpaManager TPA = new TpaManager("tpa", "[TPA]", false);
    public static final TpaManager HERE = new TpaManager("tpahere", "[TPA Here]", true);

    private static final int TIMEOUT_SECONDS = 60;
    private static final int RED = 0xFF0000;
    private static final int GRAY = 0x808080;
    private static final int GREEN = 0x51FF27;
    private static final int LIGHT_RED = 0xFF2525;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private record Request(UUID from, int secondsLeft) {}

    private final String command;
    private final String prefix;
    private final boolean here;
    /** Ziel -> offene Anfrage (pro Ziel nur eine, neue ueberschreibt wie im Original) */
    private final Map<UUID, Request> pending = new HashMap<>();
    /** Spieler, die Anfragen ausgeschaltet haben (Standard: eingeschaltet) */
    private final Set<UUID> disabled = new HashSet<>();

    private TpaManager(String command, String prefix, boolean here) {
        this.command = command;
        this.prefix = prefix;
        this.here = here;
    }

    // ---------- Befehle ----------

    public void send(ServerPlayer sender, ServerPlayer target) {
        if (disabled.contains(target.getUUID())) {
            sender.sendSystemMessage(colored(prefix + " Spieler nimmt keine Anfragen an", RED));
            return;
        }
        pending.put(target.getUUID(), new Request(sender.getUUID(), TIMEOUT_SECONDS));

        String senderName = name(sender);
        String text = here
                ? senderName + " will, dass du dich zu ihm teleportierst"
                : senderName + " will sich zu dir teleportieren";

        MutableComponent accept = Component.literal("[Annehmen]").withStyle(Style.EMPTY
                .withColor(GREEN).withClickEvent(new ClickEvent.RunCommand("/" + command + " accept")));
        MutableComponent refuse = Component.literal("[Ablehnen]").withStyle(Style.EMPTY
                .withColor(LIGHT_RED).withClickEvent(new ClickEvent.RunCommand("/" + command + " refuse")));

        MutableComponent message = Component.literal(text)
                .append(Component.literal("\n"))
                .append(accept)
                .append(Component.literal(" "))
                .append(refuse)
                .append(Component.literal("\n"))
                .append(colored("Läuft in " + TIMEOUT_SECONDS + "s ab", GRAY));

        target.sendSystemMessage(message);
        sound(target);
        sender.sendSystemMessage(Component.literal(prefix + " " + (here ? "TPA Here " : "TPA ")
                + "Anfrage an " + name(target) + " gesendet"));
    }

    public void accept(ServerPlayer accepter) {
        Request request = pending.remove(accepter.getUUID());
        if (request == null) {
            return;
        }
        ServerPlayer requester = accepter.level().getServer().getPlayerList().getPlayer(request.from());
        if (requester == null) {
            accepter.sendSystemMessage(colored(prefix + " Spieler existiert nicht oder ist offline", RED));
            return;
        }
        sound(accepter);
        sound(requester);

        // TPA: Anfragender -> Annehmender | TPAHERE: Annehmender -> Anfragender
        ServerPlayer mover = here ? accepter : requester;
        ServerPlayer destination = here ? requester : accepter;
        teleport(mover, destination);

        accepter.sendSystemMessage(Component.literal(prefix + " Anfrage akzeptiert"));
        requester.sendSystemMessage(Component.literal(prefix + " Anfrage akzeptiert"));
    }

    public void refuse(ServerPlayer refuser) {
        Request request = pending.remove(refuser.getUUID());
        if (request == null) {
            return;
        }
        ServerPlayer requester = refuser.level().getServer().getPlayerList().getPlayer(request.from());
        if (requester == null) {
            refuser.sendSystemMessage(colored(prefix + " Spieler existiert nicht oder ist offline", RED));
            return;
        }
        refuser.sendSystemMessage(Component.literal(prefix + " Anfrage abgelehnt"));
        requester.sendSystemMessage(Component.literal(prefix + " Anfrage abgelehnt"));
    }

    public void enable(ServerPlayer player) {
        disabled.remove(player.getUUID());
        save();
        player.sendSystemMessage(Component.literal(prefix + " " + (here ? "TPA Here " : "TPA ")
                + "Anfragen eingeschaltet"));
    }

    public void disable(ServerPlayer player) {
        disabled.add(player.getUUID());
        save();
        player.sendSystemMessage(Component.literal(prefix + " " + (here ? "TPA Here " : "TPA ")
                + "Anfragen ausgeschaltet"));
    }

    // ---------- Timer (jede Sekunde) ----------

    public void tick(MinecraftServer server) {
        Iterator<Map.Entry<UUID, Request>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Request> entry = it.next();
            Request r = entry.getValue();
            int left = r.secondsLeft() - 1;
            if (left > 0) {
                entry.setValue(new Request(r.from(), left));
                continue;
            }
            it.remove();
            Component expired = colored(prefix + " " + (here ? "Tpa Here " : "Tpa ") + "Anfrage abgelaufen", GRAY);
            ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
            ServerPlayer from = server.getPlayerList().getPlayer(r.from());
            if (target != null) target.sendSystemMessage(expired);
            if (from != null) from.sendSystemMessage(expired);
        }
    }

    // ---------- Hilfsfunktionen ----------

    private static void teleport(ServerPlayer mover, ServerPlayer destination) {
        // ServerPlayer#teleportTo(level, x, y, z, relatives, yRot, xRot, setCamera)
        mover.teleportTo((ServerLevel) destination.level(),
                destination.getX(), destination.getY(), destination.getZ(),
                Set.of(), destination.getYRot(), destination.getXRot(), true);
    }

    private static void sound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    private static MutableComponent colored(String text, int rgb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(rgb));
    }

    private static String name(ServerPlayer player) {
        return player.getName().getString();
    }

    // ---------- Persistenz (nur die Ein/Aus-Einstellung) ----------

    private Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("tpa-" + command + "-disabled.json");
    }

    public void load() {
        Path file = file();
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file)) {
            Type type = new TypeToken<Set<UUID>>() {}.getType();
            Set<UUID> loaded = GSON.fromJson(reader, type);
            disabled.clear();
            if (loaded != null) disabled.addAll(loaded);
        } catch (IOException | RuntimeException e) {
            TpaMod.LOGGER.warn("[TPA] Konnte {} nicht laden", file, e);
        }
    }

    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(disabled, writer);
            }
        } catch (IOException e) {
            TpaMod.LOGGER.warn("[TPA] Konnte {} nicht speichern", file, e);
        }
    }
}
