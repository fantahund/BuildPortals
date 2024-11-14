package space.frahm.buildportals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class BuildPortals extends JavaPlugin {
    // Hacky way to turn on DEBUG for plugin only
    public static Level logLevel;

    public static FileConfiguration config;
    public static Logger logger;
    public static BuildPortals plugin;
    public static PortalListener listener;
    public static HashSet<Material> activatorMaterials;

    @Override
    public void onEnable() {
        plugin = this;
        config = this.getConfig();
        logger = this.getLogger();
        logLevel = Level.OFF;
        // Set default portal building material to emerald blocks
        config.addDefault("PortalMaterial", Material.LAPIS_BLOCK.name());
        /* Set default portal activating material to be:
         * - Redstone Blocks
         * - Gold Blocks
         * - Diamond Blocks
         */
        ArrayList<String> activators = new ArrayList<>();
        //activators.add(Material.REDSTONE_BLOCK.name());
        activators.add(Material.GOLD_BLOCK.name());
        activators.add(Material.DIAMOND_BLOCK.name());
        for (Material value : Material.values()) {
            if (value.isBlock()) {
                activators.add(value.name());
            }
        }
        config.addDefault("PortalActivators", activators);
        config.addDefault("Debug", false);
        config.options().copyDefaults(true);
        this.saveConfig();

        boolean debug = config.getBoolean("Debug");
        if (debug) {
            logLevel = Level.INFO;
            logger.log(logLevel, "Debug logs on");
        }

        logger.log(logLevel, "Portal frame material set to " + config.getString("PortalMaterial"));
        activatorMaterials = new HashSet<>();
        for (String materialName : config.getStringList("PortalActivators")) {
            activatorMaterials.add(Material.getMaterial(materialName));
        }
        logger.log(logLevel, "Portal activators set to " + activatorMaterials);

        listener = new PortalListener();
        getServer().getPluginManager().registerEvents(listener, this);
        Portal.loadPortalsFromConfig();
        IncompletePortal.loadPortalsFromConfig();

        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::tickPortals, 0, 15);
    }

    @Override
    public void onDisable() {
        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
        if (cmd.getName().equalsIgnoreCase("buildportals") || cmd.getName().equalsIgnoreCase("bp")) {
            if (args.length < 1) {
                return false;
            }
            Material mat = null;
            switch (args[0].toLowerCase()) {
                case "version":
                    sender.sendMessage("This is BuildPortals version " + this.getDescription().getVersion());
                        return true;
                case "check":
                    if (!(sender instanceof Player)) {
                        sender.sendMessage("Only a player can check their location.");
                        return false;
                    }
                    Player player = (Player) sender;
                    Location loc = player.getLocation().getBlock().getLocation();
                    if (player.getVehicle() instanceof Minecart) {
                        loc.add(0,1,0);
                    }
                    if (player.getVehicle() instanceof Boat) {
                        loc.add(0,2,0);
                    } 
                    sender.sendMessage("Your location is: " + loc.toVector().toString());
                    if (Portal.isInAPortal(loc)) {
                        player.sendMessage("You ARE in a portal!");
                    } else {
                        player.sendMessage("You ARE NOT in a portal.");
                    }
                    return true;
                case "setmaterial":
                    if ( ! sender.hasPermission("buildportals.*") ) {
                        sender.sendMessage("You do not have permission to use this command.");
                        return true;
                    }
                    try {
                        mat = Material.getMaterial(args[1].toUpperCase());
                    } catch (NullPointerException | ArrayIndexOutOfBoundsException exc) {
                        sender.sendMessage("You must specify a material.");
                    }
                    if (mat == null) {
                        sender.sendMessage("Material name invalid.");
                        sender.sendMessage("Setting portal material failed.");
                        logger.warning("Setting portal material failed.");
                        return false;
                    }
                    if (!mat.isBlock()) {
                        sender.sendMessage("Material must be a placeable block type.");
                        sender.sendMessage("Setting portal material failed.");
                        logger.warning("Setting portal material failed.");
                        return false;
                    }
                    sender.sendMessage("Setting portal material to " + mat.name());
                    logger.info("Setting portal material to " + mat.name());
                    config.set("PortalMaterial", mat.name());
                    this.saveConfig();
                    sender.sendMessage("Converting existing portals to " + mat.name());
                    logger.info("Converting existing portals to " + mat.name());
                    Portal.loadPortalsFromConfig();
                    IncompletePortal.loadPortalsFromConfig();
                    return true;
                case "listmaterial":
                    if (sender.hasPermission("buildportals.listmaterial")) {
                        String matName = config.getString("PortalMaterial");
                        sender.sendMessage("Portal material is: " + matName);
                        return true;
                    } else {
                        sender.sendMessage("You do not have permission to use this command.");
                        return true;
                    }
                case "addactivator":
                    if ( ! sender.hasPermission("buildportals.*")) {
                        sender.sendMessage("You do not have permission to use this command.");
                        return true;
                    }
                    try {
                        mat = Material.getMaterial(args[1].toUpperCase());
                    } catch (NullPointerException | ArrayIndexOutOfBoundsException exc) {
                        sender.sendMessage("You must specify a material.");
                    }
                    if (mat == null) {
                        sender.sendMessage("Material name invalid.");
                        sender.sendMessage("Adding activator material failed.");
                        logger.warning("Adding activator material failed.");
                        return false;
                    }
                    if (!mat.isBlock()) {
                        sender.sendMessage("Material must be a placeable block type.");
                        sender.sendMessage("Adding activator material failed.");
                        logger.warning("Adding activator material failed.");
                        return false;
                    }
                    if (activatorMaterials.contains(mat)) {
                        sender.sendMessage("That is already an activator material.");
                        logger.warning(sender.getName() + " attempted to add an already configured activator material.");
                        return false;
                    }
                    activatorMaterials.add(mat);
                    sender.sendMessage("Adding " + mat.name() + " as an activator.");
                    logger.info("Adding " + mat.name() + " as an activator.");
                    config.set("PortalActivators", activatorMaterials);
                    this.saveConfig();
                    return true;
                case "removeactivator":
                    if ( ! sender.hasPermission("buildportals.*")) {
                        sender.sendMessage("You do not have permission to use this command.");
                        return true;
                    }
                    try {
                        mat = Material.getMaterial(args[1].toUpperCase());
                    } catch (NullPointerException | ArrayIndexOutOfBoundsException exc) {
                        sender.sendMessage("You must specify a material.");
                    }
                    if (mat == null) {
                        sender.sendMessage("Removing activator material failed.");
                        logger.warning("Removing activator material failed.");
                        return false;
                    }
                    if (!activatorMaterials.contains(mat)) {
                        sender.sendMessage("That is not an activator material.");
                        logger.warning(sender.getName() + " attempted to remove to an unconfigured activator material.");
                        return false;
                    }
                    activatorMaterials.remove(mat);
                    sender.sendMessage("Removing " + mat.name() + " from activators.");
                    logger.info("Removing " + mat.name() + " from activators.");
                    config.set("PortalActivators", activatorMaterials);
                    this.saveConfig();
                    return true;
                case "listactivators":
                    if (sender.hasPermission("buildportals.listactivators")) {
                    sender.sendMessage("Activators are: " + activatorMaterials);
                    return true;
                    } else {
                        sender.sendMessage("You do not have permission to use this command.");
                        return true;
                    }
                default:
                    sender.sendMessage("BuildPortals command usage:");
                    sender.sendMessage("  /BP Version ");
                    sender.sendMessage("    Returns the BuildPortals Version info.");
                    sender.sendMessage("  /BP Check ");
                    sender.sendMessage("    Returns whether you are currently in a portal.");
                    sender.sendMessage("  /BP SetMaterial <Material_Name>");
                    sender.sendMessage("    Sets the material from which portals should be built. Note that this will change all existing portals to this material.");
                    sender.sendMessage("  /BP AddActivator <Material_Name>");
                    sender.sendMessage("    Adds to the list of activator materials.");
                    sender.sendMessage("  /BP RemoveActivator <Material_Name>");
                    sender.sendMessage("    Removes an activator material.");
                    sender.sendMessage("  /BP ListMaterial");
                    sender.sendMessage("    Lists the configured portal material.");
                    sender.sendMessage("  /BP ListActivators");
                    sender.sendMessage("    Lists all configured activator materials.");
                    return false;
            }
        }
        return true;
    }

    public void tickPortals() {
        Bukkit.getWorlds().forEach(world -> Portal.getInteriors().getOrDefault(world.getName(), new HashSet<>()).forEach(vector -> {
            Location location = new Location(world, vector.getBlockX(), vector.getBlockY(), vector.getBlockZ());
            Chunk chunk = world.getChunkAt(location.getBlockX(), location.getBlockZ());
            if (chunk.isLoaded()) {
                world.getPlayers().forEach(player -> {
                    if (distanceSquared(player.getLocation(), location) <= 20) {
                        player.playEffect(location, Effect.MOBSPAWNER_FLAMES, 20);
                    }
                });
            }
        }));
    }


    public double distanceSquared(Location o, Location loc) {
        if (o.getWorld() != null && loc.getWorld() != null) {
            if (o.getWorld() != loc.getWorld()) {
                String var10002 = loc.getWorld().getName();
                throw new IllegalArgumentException("Cannot measure distance between " + var10002 + " and " + o.getWorld().getName());
            } else {
                return square(loc.getX() - o.getX()) + square(loc.getY() - o.getY()) + square(loc.getZ() - o.getZ());
            }
        } else {
            throw new IllegalArgumentException("Cannot measure distance to a null world");
        }
    }

    public static double square(double num) {
        return num * num;
    }
}