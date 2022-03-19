package xyz.jpenilla.squaremap.paper;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.bstats.bukkit.Metrics;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.NonNull;
import xyz.jpenilla.squaremap.api.Squaremap;
import xyz.jpenilla.squaremap.common.ServerAccess;
import xyz.jpenilla.squaremap.common.SquaremapCommon;
import xyz.jpenilla.squaremap.common.SquaremapPlatform;
import xyz.jpenilla.squaremap.common.inject.ModulesConfiguration;
import xyz.jpenilla.squaremap.common.task.UpdatePlayers;
import xyz.jpenilla.squaremap.common.task.UpdateWorldData;
import xyz.jpenilla.squaremap.paper.data.PaperMapWorld;
import xyz.jpenilla.squaremap.paper.inject.module.PaperModule;
import xyz.jpenilla.squaremap.paper.listener.MapUpdateListeners;
import xyz.jpenilla.squaremap.paper.listener.PlayerListener;
import xyz.jpenilla.squaremap.paper.listener.WorldLoadListener;
import xyz.jpenilla.squaremap.paper.network.PaperNetworking;
import xyz.jpenilla.squaremap.paper.util.BukkitRunnableAdapter;

public final class SquaremapPaper extends JavaPlugin implements SquaremapPlatform {
    private Injector injector;
    private SquaremapCommon common;
    private PaperWorldManager worldManager;
    private PaperPlayerManager playerManager;
    private BukkitRunnable updateWorldData;
    private BukkitRunnable updatePlayers;
    private MapUpdateListeners mapUpdateListeners;
    private WorldLoadListener worldLoadListener;

    @Override
    public void onEnable() {
        try {
            Class.forName("com.destroystokyo.paper.PaperConfig");
        } catch (ClassNotFoundException e) {
            this.getLogger().severe("squaremap requires Paper or one of its forks to run. Get Paper from https://papermc.io/downloads");
            this.getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.injector = Guice.createInjector(
            ModulesConfiguration.create(this)
                .mapWorldFactory(PaperMapWorld.Factory.class)
                .withModule(new PaperModule(this))
                .done()
        );

        this.common = this.injector.getInstance(SquaremapCommon.class);
        this.getServer().getServicesManager().register(Squaremap.class, this.common.api(), this, ServicePriority.Normal);

        PaperNetworking.register(this);

        this.getServer().getPluginManager().registerEvents(this.injector.getInstance(PlayerListener.class), this);

        new Metrics(this, 13571); // https://bstats.org/plugin/bukkit/squaremap/13571

        this.getServer().getScheduler().runTask(this, this.common::updateCheck);
    }

    @Override
    public void onDisable() {
        PaperNetworking.unregister(this);
        if (this.common != null) {
            this.getServer().getServicesManager().unregister(Squaremap.class, this.common.api());
            this.common.shutdown();
        }
    }

    @Override
    public void startCallback() {
        this.playerManager = this.injector.getInstance(PaperPlayerManager.class);

        this.updatePlayers = new BukkitRunnableAdapter(this.injector.getInstance(UpdatePlayers.class));
        this.updatePlayers.runTaskTimer(this, 20, 20);

        this.updateWorldData = new BukkitRunnableAdapter(this.injector.getInstance(UpdateWorldData.class));
        this.updateWorldData.runTaskTimer(this, 0, 20 * 5);

        this.worldManager = this.injector.getInstance(PaperWorldManager.class);
        this.worldManager.start();

        this.mapUpdateListeners = this.injector.getInstance(MapUpdateListeners.class);
        this.mapUpdateListeners.register();

        this.worldLoadListener = new WorldLoadListener(this);
        this.getServer().getPluginManager().registerEvents(this.worldLoadListener, this);
    }

    @Override
    public void stopCallback() {
        if (this.mapUpdateListeners != null) {
            this.mapUpdateListeners.unregister();
            this.mapUpdateListeners = null;
        }

        if (this.worldLoadListener != null) {
            HandlerList.unregisterAll(this.worldLoadListener);
            this.worldLoadListener = null;
        }

        if (this.updatePlayers != null) {
            if (!this.updatePlayers.isCancelled()) {
                this.updatePlayers.cancel();
            }
            this.updatePlayers = null;
        }

        if (this.updateWorldData != null) {
            if (!this.updateWorldData.isCancelled()) {
                this.updateWorldData.cancel();
            }
            this.updateWorldData = null;
        }

        if (this.worldManager != null) {
            this.worldManager.shutdown();
            this.worldManager = null;
        }

        if (this.playerManager != null) {
            this.playerManager = null;
        }

        this.getServer().getScheduler().cancelTasks(this);
    }

    @Override
    public @NonNull ServerAccess serverAccess() {
        return this.injector.getInstance(ServerAccess.class);
    }

    @Override
    public @NonNull String version() {
        return this.getDescription().getVersion();
    }

    @Override
    public @NonNull PaperWorldManager worldManager() {
        return this.worldManager;
    }

    @Override
    public @NonNull PaperPlayerManager playerManager() {
        return this.playerManager;
    }
}
