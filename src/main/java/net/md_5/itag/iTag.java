package net.md_5.itag;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import lombok.Getter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
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
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.comphenix.protocol.reflect.accessors.MethodAccessor;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.EnumWrappers.PlayerInfoAction;
import com.comphenix.protocol.wrappers.PlayerInfoData;
import com.comphenix.protocol.wrappers.WrappedGameProfile;
import com.google.common.base.Preconditions;

@SuppressWarnings("deprecation")
public class iTag extends JavaPlugin implements Listener {
	
	private static final FieldAccessor propertiesField = Accessors.getFieldAccessor(MinecraftReflection.getGameProfileClass(), "properties", true);
	private static final FieldAccessor uuidField = Accessors.getFieldAcccessorOrNull(MinecraftReflection.getGameProfileClass(), "id", UUID.class);
	private static final MethodAccessor playerProfile = Accessors.getMethodAccessor(MinecraftReflection.getCraftPlayerClass(), "getProfile");
	
	@Getter
	private static iTag instance;
	
	@Override
	public void onEnable() {
		instance = this;
		new TagAPI(this);
		
		getServer().getPluginManager().registerEvents(this, this);
		
		ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.Play.Server.PLAYER_INFO) {

			public void onPacketSending(PacketEvent event) {
				if (event.getPacket().getPlayerInfoAction().read(0) != PlayerInfoAction.ADD_PLAYER) return;
				
				List<PlayerInfoData> list = event.getPacket().getPlayerInfoDataLists().read(0);
				for (int i = 0; i < list.size(); i++) {
					PlayerInfoData data = list.get(i);
					if (data == null || data.getProfile() == null) continue;
					WrappedGameProfile result = getSentName(data.getProfile(), event.getPlayer());
					list.set(i, new PlayerInfoData(result, data.getPing(), data.getGameMode(), data.getDisplayName()));
				}
				event.getPacket().getPlayerInfoDataLists().write(0, list);
			};

		});
	}

	@Override
	public void onDisable() {
		ProtocolLibrary.getProtocolManager().removePacketListeners(this);
		instance = null;
	}
	
	private WrappedGameProfile getSentName(WrappedGameProfile sent, Player destinationPlayer) {
		Player namedPlayer = Bukkit.getPlayer(sent.getUUID());
		
		sent = checkClone(namedPlayer, sent);
		PlayerReceiveNameTagEvent oldEvent = new PlayerReceiveNameTagEvent(destinationPlayer, namedPlayer, sent.getName());
		getServer().getPluginManager().callEvent(oldEvent);

		AsyncPlayerReceiveNameTagEvent newEvent = new AsyncPlayerReceiveNameTagEvent(destinationPlayer, namedPlayer, oldEvent.getTag(), sent.getUUID());
		getServer().getPluginManager().callEvent(newEvent);

		PlayerReceiveGameProfileEvent profileEvent = new PlayerReceiveGameProfileEvent(destinationPlayer, namedPlayer, sent);
		profileEvent.setName(newEvent.getTag());
		getServer().getPluginManager().callEvent(profileEvent);
		
		WrappedGameProfile newProfile = profileEvent.getGameProfile();
		
		if (!newProfile.getUUID().equals(namedPlayer.getUniqueId()))
			uuidField.set(newProfile.getHandle(), namedPlayer.getUniqueId());
		
		return newProfile;
	}
	
	public WrappedGameProfile checkClone(Player player, WrappedGameProfile original) {
		return original.getHandle() == playerProfile.invoke(player) ? cloneProfile(original) : original;
	}
	
	public WrappedGameProfile cloneProfile(WrappedGameProfile original) {
		WrappedGameProfile result = new WrappedGameProfile(original.getUUID(), original.getName());
		propertiesField.set(result.getHandle(), propertiesField.get(original.getHandle()));
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
