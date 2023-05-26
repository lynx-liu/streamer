package com.vrviu.opengl;

public class RenderConfig {
    public float sharp = 0.0f;
    public int brightness = 0;
    public int contrast = 0;
    public int saturation = 0;
    public boolean dynamicFps = false;
    public boolean showText = false;

    public RenderConfig() {

    }

    public RenderConfig(float sharp, int brightness, int contrast, int saturation, boolean dynamicFps, boolean showText) {
        this.sharp = sharp;
        this.brightness = brightness;
        this.contrast = contrast;
        this.saturation = saturation;
        this.dynamicFps = dynamicFps;
        this.showText = showText;
    }

    public boolean needRender() {
        return sharp>0 || brightness!=0 || contrast!=0 || saturation !=0 || dynamicFps || showText;
    }
}
