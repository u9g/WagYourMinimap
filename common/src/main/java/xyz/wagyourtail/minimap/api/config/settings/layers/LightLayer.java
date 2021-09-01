package xyz.wagyourtail.minimap.api.config.settings.layers;

import xyz.wagyourtail.minimap.client.gui.image.BlockLightImageStrategy;

public class LightLayer extends AbstractLayerOptions<BlockLightImageStrategy> {
    @Override
    public BlockLightImageStrategy compileLayer() {
        return new BlockLightImageStrategy();
    }

}
