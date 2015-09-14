package com.starfire1337.metrics;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.json.simple.JSONObject;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;

@SuppressWarnings("all")
public class Metrics {

    private final int version = 3;

    private Plugin plugin;
    private YamlConfiguration config;
    private ScheduledExecutorService scheduler;
    private Runnable metricsRunnable;
    private ScheduledFuture<?> scheduledFuture;
    private boolean forceEnable = false;


    public Metrics(Plugin plugin) {
        if(plugin == null ||  metricsRunnable != null || scheduledFuture != null || scheduler != null)
            return;

        this.plugin = plugin;

        try {
            this.init();
        } catch(IOException e) {
            System.out.println("Error while starting Metrics:");
            e.printStackTrace();
        }
    }

    public Metrics(Plugin plugin, boolean forceEnable) {
        this(plugin);
        this.forceEnable = forceEnable;
    }

    private void init() throws IOException {
        File dataFolder = new File("plugins" + File.separator + "Metrics");
        if(!dataFolder.exists() || !dataFolder.isDirectory())
            dataFolder.mkdir();
        File configFile = new File(dataFolder, "metrics.yml");
        if(!configFile.exists())
            configFile.createNewFile();

        config = YamlConfiguration.loadConfiguration(configFile);

        if(!config.isSet("enabled") || !config.isBoolean("enabled"))
            config.set("enabled", true);
        if(!config.isSet("sid") || !config.isString("sid") || config.getString("sid").length() != 48) {
            SecureRandom random = new SecureRandom();
            String sid = new BigInteger(240, random).toString(32);
            config.set("sid", sid);
        }
        config.save(configFile);

        if(config.getBoolean("enabled") || this.forceEnable)
            this.startMetrics();
    }

    private void startMetrics() {
        scheduler = Executors.newScheduledThreadPool(1, new MetricsThreadFactory(plugin.getName() + " Metrics Thread (v" + version + ")"));
        metricsRunnable = new MetricsRunnable();
        scheduledFuture = scheduler.scheduleAtFixedRate(metricsRunnable, 0, 30, TimeUnit.MINUTES);
    }

    private class MetricsThreadFactory implements ThreadFactory {

        private String name;

        public MetricsThreadFactory(String name) {
            this.name = name;
        }

        public Thread newThread(Runnable runnable) {
            return new Thread(runnable, name);
        }

    }

    private class MetricsRunnable implements Runnable {

        public void run() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("sversion", plugin.getServer().getVersion());
                jsonObject.put("sid", config.getString("sid"));
                jsonObject.put("splayers", plugin.getServer().getOnlinePlayers().size());
                jsonObject.put("pname", plugin.getName());
                jsonObject.put("pversion", plugin.getDescription().getVersion());
                jsonObject.put("osname", System.getProperty("os.name"));
                jsonObject.put("osarch", System.getProperty("os.arch").replace("amd64", "x86_64"));
                jsonObject.put("osversion", System.getProperty("os.version"));
                jsonObject.put("jversion", System.getProperty("java.version"));
                jsonObject.put("cores", Runtime.getRuntime().availableProcessors() + "");

                URL url = new URL("http://metrics.starfire1337.com/upload.php");
                HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();

                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setRequestProperty("User-Agent", "Metrics v" + version);

                httpURLConnection.setDoOutput(true);
                DataOutputStream dataOutputStream = new DataOutputStream(httpURLConnection.getOutputStream());
                dataOutputStream.writeBytes(jsonObject.toString());
                dataOutputStream.flush();
                dataOutputStream.close();

                httpURLConnection.getInputStream();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

    }

    public void shutdown() {
        if(scheduledFuture == null || scheduler == null)
            return;
        scheduledFuture.cancel(true);
        scheduler.shutdownNow();
    }

}