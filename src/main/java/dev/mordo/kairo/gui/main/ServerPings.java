package dev.mordo.kairo.gui.main;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Asks servers for their status (MOTD, players, ping, icon) the same way the vanilla server list does:
 * the blocking part runs on a few background threads, results land in the {@link ServerData}.
 */
public final class ServerPings implements AutoCloseable {
	private static final ExecutorService POOL = new ThreadPoolExecutor(0, 5, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), r -> {
		Thread t = new Thread(r, "Kairo server ping");
		t.setDaemon(true);
		return t;
	});

	private final ServerStatusPinger pinger = new ServerStatusPinger();

	/** Pings once (if not already pinged). {@code onDataChanged} runs on the client thread when icon/data changed. */
	public void ping(ServerData data, Runnable onDataChanged) {
		if (data.state() != ServerData.State.INITIAL) {
			return;
		}
		data.setState(ServerData.State.PINGING);
		data.motd = Component.empty();
		data.status = Component.empty();
		Minecraft mc = Minecraft.getInstance();
		POOL.execute(() -> {
			try {
				pinger.pingServer(data, () -> mc.execute(onDataChanged), () -> data.setState(
						data.protocol == SharedConstants.getCurrentVersion().protocolVersion()
							? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE),
					EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
			} catch (UnknownHostException e) {
				data.setState(ServerData.State.UNREACHABLE);
				data.motd = Component.literal("Can't resolve the address");
			} catch (Exception e) {
				data.setState(ServerData.State.UNREACHABLE);
				data.motd = Component.literal("Can't connect to the server");
			}
		});
	}

	/** Pings again from scratch. */
	public void refresh(ServerData data, Runnable onDataChanged) {
		data.setState(ServerData.State.INITIAL);
		ping(data, onDataChanged);
	}

	/** Call every client tick while the screen is open (finishes pings, handles timeouts). */
	public void tick() {
		pinger.tick();
	}

	@Override
	public void close() {
		pinger.removeAll();
	}

	/** 0..5 bars for a ping in ms (like vanilla's signal icon). */
	public static int bars(long ping) {
		if (ping < 0) {
			return 0;
		}
		return ping < 150 ? 5 : ping < 300 ? 4 : ping < 600 ? 3 : ping < 1000 ? 2 : 1;
	}

	public static int pingColor(long ping) {
		return ping < 150 ? 0xFF6EE7A0 : ping < 300 ? 0xFFB8E36A : ping < 600 ? 0xFFFFC145 : 0xFFFF7A5A;
	}
}
