package net.md_5.itag;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;
import net.minecraft.util.com.mojang.authlib.GameProfile;
import net.minecraft.util.com.mojang.authlib.properties.PropertyMap;
import net.minecraft.util.com.mojang.util.UUIDTypeAdapter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.kitteh.tag.AsyncPlayerReceiveNameTagEvent;
import org.kitteh.tag.PlayerReceiveGameProfileEvent;
import org.kitteh.tag.PlayerReceiveNameTagEvent;
import org.kitteh.tag.TagAPI;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.ConstructorAccessor;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftFields;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.base.Preconditions;

@SuppressWarnings("deprecation")
public class iTag extends JavaPlugin implements Listener {
	
	private static final MethodAccessor getProtocolVersion = Accessors.getMethodAccessor(MinecraftReflection.getMinecraftClass("NetworkManager"), "getVersion");
	private static final MethodAccessor playerProfile = Accessors.getMethodAccessor(MinecraftReflection.getCraftPlayerClass(), "getProfile");
	private static final FieldAccessor propertiesField = Accessors.getFieldAcccessorOrNull(GameProfile.class, "properties", PropertyMap.class);
	private static final ConstructorAccessor wrappedProfile = Accessors.getConstructorAccessor(WrappedGameProfile.class, Object.class);
	private static final FieldAccessor uuidField = Accessors.getFieldAcccessorOrNull(GameProfile.class, "id", UUID.class);
	
	@Getter
	private static iTag instance;
	private Map<Integer, Player> entityIdMap;
	
	@Override
	public void onEnable() {
		instance = this;
		entityIdMap = new HashMap<Integer, Player>();
		new TagAPI(this);

		for (Player player : getServer().getOnlinePlayers()) {
			entityIdMap.put(player.getEntityId(), player);
		}

		getServer().getPluginManager().registerEvents(this, this);
		
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.Play.Server.PLAYER_INFO) {

			public void onPacketSending(PacketEvent event) {
				if (getProcotolVersion(event.getPlayer()) >= 47 && event.getPacket().getIntegers().read(0) == 0) {
					WrappedGameProfile original = event.getPacket().getGameProfiles().read(0);
					event.getPacket().getGameProfiles().write(0, getSentName(Bukkit.getPlayer(original.getUUID()), original, event.getPlayer()));
				}
			};

		});
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.Play.Server.NAMED_ENTITY_SPAWN) {
			@Override
			public void onPacketSending(final PacketEvent event) {
				if (getProcotolVersion(event.getPlayer()) < 47) {
					event.getPacket().getGameProfiles().write(0, getSentName(event.getPacket().getIntegers().read(0), event.getPacket().getGameProfiles().read(0), event.getPlayer()));
				}		
			}
		});
		
		wrappedProfile.getConstructor().setAccessible(true);
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		entityIdMap.put(event.getPlayer().getEntityId(), event.getPlayer());
	}

	@EventHandler
	public void onQuit(PlayerQuitEvent event) {
		entityIdMap.remove(event.getPlayer().getEntityId());
	}

	@Override
	public void onDisable() {
		ProtocolLibrary.getProtocolManager().removePacketListeners(this);

		entityIdMap.clear();
		entityIdMap = null;
		instance = null;
	}

	private WrappedGameProfile getSentName(int sentEntityId, WrappedGameProfile sent, Player destinationPlayer) {
		Preconditions.checkState(getServer().isPrimaryThread(), "Can only process events on main thread.");

		Player namedPlayer = entityIdMap.get(sentEntityId);
		if (namedPlayer == null) {
			return sent;
		}
		return getSentName(namedPlayer, sent, destinationPlayer);
	}
	
	private WrappedGameProfile getSentName(Player namedPlayer, WrappedGameProfile sent, Player destinationPlayer) {
		if (namedPlayer == null || sent == null)
			return sent;
		
		sent = checkClone(namedPlayer, sent);
		
		PlayerReceiveNameTagEvent oldEvent = new PlayerReceiveNameTagEvent(destinationPlayer, namedPlayer, sent.getName());
		getServer().getPluginManager().callEvent(oldEvent);

		AsyncPlayerReceiveNameTagEvent newEvent = new AsyncPlayerReceiveNameTagEvent(destinationPlayer, namedPlayer, oldEvent.getTag(), sent.getId().contains("-") ? UUID.fromString(sent.getId()) : UUIDTypeAdapter.fromString(sent.getId()));
		getServer().getPluginManager().callEvent(newEvent);

		PlayerReceiveGameProfileEvent profileEvent = new PlayerReceiveGameProfileEvent(destinationPlayer, namedPlayer, (GameProfile) sent.getHandle());
		profileEvent.setName(newEvent.getTag());
		getServer().getPluginManager().callEvent(profileEvent);
		
		GameProfile newProfile = profileEvent.getGameProfile();
		uuidField.set(newProfile, namedPlayer.getUniqueId());
		
		return (WrappedGameProfile) wrappedProfile.invoke(newProfile);
	}

	private int getProcotolVersion(Player player) {
		return (Integer) getProtocolVersion.invoke(MinecraftFields.getNetworkManager(player));
	}
	
	public GameProfile checkClone(Player player, GameProfile profile) {
		return profile == playerProfile.invoke(player) ? profile : clone(profile);
	}
	
	public WrappedGameProfile checkClone(Player player, WrappedGameProfile profile) {
		return profile.getHandle() == playerProfile.invoke(player) ? profile : clone(profile);
	}
	
	public WrappedGameProfile clone(WrappedGameProfile original) {
		return (WrappedGameProfile) wrappedProfile.invoke(clone((GameProfile) original.getHandle()));
	}
	
	private GameProfile clone(GameProfile original) {
		GameProfile result = new GameProfile(original.getId(), original.getName());
		propertiesField.set(result, original.getProperties());
		return result;
	}

	public void refreshPlayer(Player player) {
		Preconditions.checkState(isEnabled(), "Not Enabled!");
		Preconditions.checkNotNull(player, "player");

		for (Player playerFor : player.getWorld().getPlayers()) {
			refreshPlayer(player, playerFor);
		}
	}

	public void refreshPlayer(final Player player, final Player forWhom) {
		Preconditions.checkState(isEnabled(), "Not Enabled!");
		Preconditions.checkNotNull(player, "player");
		Preconditions.checkNotNull(forWhom, "forWhom");

		if (player != forWhom && player.getWorld() == forWhom.getWorld() && forWhom.canSee(player)) {
			forWhom.hidePlayer(player);
			getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
				public void run() {
					forWhom.showPlayer(player);
				}
			}, 2);
		}
	}

	public void refreshPlayer(Player player, Set<Player> forWhom) {
		Preconditions.checkState(isEnabled(), "Not Enabled!");
		Preconditions.checkNotNull(player, "player");
		Preconditions.checkNotNull(forWhom, "forWhom");

		for (Player playerFor : forWhom) {
			refreshPlayer(player, playerFor);
		}
	}
}
