package com.philesc.map_cache.mixin.client;

import com.philesc.map_cache.ClientMaps;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.map.MapState;
import net.minecraft.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemFrameEntity.class)
public class ItemFrameMixin {
	private static final Logger LOGGER = LoggerFactory.getLogger("client_maps_itemframe");

	@Inject(at = @At("RETURN"), method = "getMapId", cancellable = true)
	private void onGetMapId(CallbackInfoReturnable<MapIdComponent> cir) {
		MapIdComponent mapIdComponent = cir.getReturnValue();
		if (mapIdComponent == null) return;

		Integer mapId = mapIdComponent.id();
		if (mapId == null) {
			return;
		}

		ItemFrameEntity itemFrame = (ItemFrameEntity) (Object) this;
		if (!(itemFrame.getWorld() instanceof ClientWorld world)) return;

		if (ClientMaps.pending.contains(mapId)) return;

		MapState state = world.getMapState(mapIdComponent);

		if (state == null) {
			// register this id as pending load from disk
			ClientMaps.pending.add(mapId);

			Util.getIoWorkerExecutor().execute(() -> {
				// The map state does not exist, create a dummy one
				MapState dummyState = MapState.of(0, 0, ClientMaps.MARKER, false, false, null);
				byte[] colors = ClientMaps.getMap(mapId);

				if (colors == null) {
					ClientMaps.pending.remove(mapId);
					return;
				}

				dummyState.colors = colors;

				world.putClientsideMapState(mapIdComponent, dummyState);
				ClientMaps.pending.remove(mapId);
			});
		}
	}
}
