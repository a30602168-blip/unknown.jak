package unkown.jar;

import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import unkown.jar.director.EncounterDirector;
import unkown.jar.entity.ModEntities;
import unkown.jar.entity.ModSounds;

public class UnkownJar implements ModInitializer {
	public static final String MOD_ID = "unkownjar";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		UnkownConfig.load();
		ModSounds.init();
		ModEntities.init();
		EncounterDirector.init();
		LOGGER.info("unknown.jar loaded. Do not stare into the dark for too long.");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
