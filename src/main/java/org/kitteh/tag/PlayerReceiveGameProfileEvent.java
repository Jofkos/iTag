package org.kitteh.tag;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.util.com.mojang.authlib.GameProfile;
import net.minecraft.util.com.mojang.authlib.properties.Property;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import com.comphenix.protocol.reflect.accessors.Accessors;
import com.comphenix.protocol.reflect.accessors.FieldAccessor;
import com.google.common.base.Preconditions;

public class PlayerReceiveGameProfileEvent extends Event {

	private static final HandlerList handlers = new HandlerList();
	/* ======================================================================== */
	
	private static final FieldAccessor nameField = Accessors.getFieldAcccessorOrNull(GameProfile.class, "name", String.class);
	
	@Getter
	private final Player player;
	@Getter
	private final Player namedPlayer;
	@Getter @Setter
	private GameProfile gameProfile;
	
	public PlayerReceiveGameProfileEvent(Player who, Player namedPlayer, GameProfile profile) {
		Preconditions.checkNotNull(who, "who");
		Preconditions.checkNotNull(namedPlayer, "namedPlayer");
		Preconditions.checkNotNull(profile, "gameProfile");
		
		this.player = who;
		this.namedPlayer = namedPlayer;
		this.gameProfile = profile;
	}
	
	public void setName(String name) {
		Preconditions.checkNotNull(name, "name");
		nameField.set(gameProfile, name.substring(0, Math.min(name.length(), 16)));
	}
	
	public void setTexture(String base64texture, String signature) {
		gameProfile.getProperties().removeAll("textures");
		gameProfile.getProperties().put("textures", new Property("textures", base64texture, signature));
	}
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}