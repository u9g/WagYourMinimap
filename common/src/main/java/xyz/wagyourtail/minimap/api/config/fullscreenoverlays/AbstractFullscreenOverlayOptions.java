package xyz.wagyourtail.minimap.api.config.fullscreenoverlays;

import xyz.wagyourtail.minimap.client.gui.screen.map.AbstractFullscreenOverlay;

public abstract class AbstractFullscreenOverlayOptions<T extends AbstractFullscreenOverlay> {

    public abstract T compileOverlay();
}
