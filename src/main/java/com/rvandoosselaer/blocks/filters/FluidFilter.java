package com.rvandoosselaer.blocks.filters;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Plane;
import com.jme3.math.Vector3f;
import com.jme3.post.Filter;
import com.jme3.renderer.Camera;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.GeometryList;
import com.jme3.renderer.queue.OpaqueComparator;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.renderer.queue.TransparentComparator;
import com.jme3.scene.Geometry;
import com.jme3.scene.SceneGraphVisitorAdapter;
import com.jme3.scene.Spatial;
import com.jme3.texture.FrameBuffer;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;
import com.jme3.ui.Picture;
import com.jme3.water.ReflectionProcessor;
import com.jme3.water.WaterUtils;
import lombok.Getter;

import java.util.List;

/**
 * @author: rvandoosselaer
 */
public class FluidFilter extends Filter {

    private RenderManager renderManager;
    private ViewPort viewPort;
    private Material material;
    private FrameBuffer fluidDepthBuffer;
    private FrameBuffer sceneDepthBuffer;
    private final GeometryList fluidGeometryList = new GeometryList(new TransparentComparator());
    private Pass reflectionPass;
    private Spatial reflectionScene;
    private Spatial rootScene;
    private ViewPort reflectionView;
    private Camera reflectionCam;
    private ReflectionProcessor reflectionProcessor;
    private float waterHeight = 0.0f;
    private Plane plane = new Plane(Vector3f.UNIT_Y, waterHeight);
    private int reflectionMapSize = 512;
    private boolean underWater;
    private float reflectionDisplace = 30;
    private Picture dispReflection;

    @Getter
    private ColorRGBA fadeColor = new ColorRGBA(0.0289f, 0.136f, 0.453f, 1.0f);
    @Getter
    private float fadeDepth = 6.0f;
    @Getter
    private float shorelineSize = 0.2f;
    @Getter
    private ColorRGBA shorelineColor = new ColorRGBA(0.406f, 0.615f, 1.0f, 1f);
    @Getter
    private boolean distortion = true;
    @Getter
    private float distortionStrengthX = 0.0015f;
    @Getter
    private float distortionStrengthY = 0.0f;
    @Getter
    private float distortionAmplitudeX = 15f;
    @Getter
    private float distortionAmplitudeY = 30f;
    @Getter
    private float distortionSpeed = 3.0f;

    public FluidFilter() {
        super("Fluid filter");
    }

    @Override
    protected boolean isRequiresSceneTexture() {
        return true;
    }

    protected boolean isRequiresDepthTexture() {
        return false;
    }

    protected boolean isRequiresBilinear() {
        return true;
    }

    @Override
    protected void initFilter(AssetManager manager, RenderManager renderManager, ViewPort vp, int w, int h) {
        rootScene = vp.getScenes().get(0);

        if (reflectionScene == null) {
            reflectionScene = rootScene;
        }

        reflectionPass = new Pass();
        reflectionPass.init(renderManager.getRenderer(), reflectionMapSize, reflectionMapSize, Image.Format.RGBA8, Image.Format.Depth);
        reflectionCam = new Camera(reflectionMapSize, reflectionMapSize);
        reflectionView = new ViewPort("reflectionView", reflectionCam);
        reflectionView.setClearFlags(true, true, true);
        reflectionView.attachScene(reflectionScene);
        reflectionView.setOutputFrameBuffer(reflectionPass.getRenderFrameBuffer());
        plane = new Plane(Vector3f.UNIT_Y, new Vector3f(0, waterHeight, 0).dot(Vector3f.UNIT_Y));
        reflectionProcessor = new ReflectionProcessor(reflectionCam, reflectionPass.getRenderFrameBuffer(), plane);
        reflectionProcessor.setReflectionClipPlane(plane);
        reflectionView.addProcessor(reflectionProcessor);

        material = new Material(manager, "Blocks/MatDefs/FluidDepth.j3md");
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        material.setColor("FadeColor", fadeColor);
        material.setFloat("FadeDepth", fadeDepth);
        material.setFloat("ShorelineSize", shorelineSize);
        material.setColor("ShorelineColor", shorelineColor);
        material.setBoolean("UseDistortion", distortion);
        material.setFloat("DistortionStrengthX", distortionStrengthX);
        material.setFloat("DistortionStrengthY", distortionStrengthY);
        material.setFloat("DistortionAmplitudeX", distortionAmplitudeX);
        material.setFloat("DistortionAmplitudeY", distortionAmplitudeY);
        material.setFloat("DistortionSpeed", distortionSpeed);
        material.setTexture("ReflectionMap", reflectionPass.getRenderedTexture());
        material.setFloat("ReflectionDisplace", reflectionDisplace);
        material.setFloat("WaterHeight", waterHeight);

        fluidDepthBuffer = new FrameBuffer(w, h, 1);
        sceneDepthBuffer = new FrameBuffer(w, h, 1);
        Texture2D fluidDepthTexture = new Texture2D(w, h, 1, Image.Format.Depth);
        Texture2D sceneDepthTexture = new Texture2D(w, h, 1, Image.Format.Depth);
        fluidDepthBuffer.setDepthTexture(fluidDepthTexture);
        sceneDepthBuffer.setDepthTexture(sceneDepthTexture);

        material.setTexture("FluidDepthTexture", fluidDepthTexture);
        material.setTexture("SceneDepthTexture", sceneDepthTexture);

        this.renderManager = renderManager;
        this.viewPort = vp;

        dispReflection = new Picture("dispReflection");
        dispReflection.setTexture(manager, reflectionPass.getRenderedTexture(), false);
    }

    @Override
    protected Material getMaterial() {
        return material;
    }

    @Override
    protected void preFrame(float tpf) {
        Camera sceneCam = viewPort.getCamera();
//        biasMatrix.mult(sceneCam.getViewProjectionMatrix(), textureProjMatrix);
//        material.setMatrix4("TextureProjMatrix", textureProjMatrix);
//        material.setVector3("CameraPosition", sceneCam.getLocation());

        WaterUtils.updateReflectionCam(reflectionCam, plane, sceneCam);


        //if we're under water no need to compute reflection
        if (sceneCam.getLocation().y >= waterHeight) {
            boolean rtb = true;
            if (!renderManager.isHandleTranslucentBucket()) {
                renderManager.setHandleTranslucentBucket(true);
                rtb = false;
            }
            renderManager.renderViewPort(reflectionView, tpf);
            if (!rtb) {
                renderManager.setHandleTranslucentBucket(false);
            }
            renderManager.setCamera(sceneCam, false);
            renderManager.getRenderer().setFrameBuffer(viewPort.getOutputFrameBuffer());


            underWater = false;
        } else {
            underWater = true;
        }
    }

    @Override
    protected void postQueue(RenderQueue queue) {
        Renderer renderer = renderManager.getRenderer();

        // create the fluid depth texture
        if (fluidGeometryList.size() > 0) {
            renderer.setFrameBuffer(fluidDepthBuffer);
            renderer.clearBuffers(true, true, true);
            renderManager.renderGeometryList(fluidGeometryList);
        }

        // retrieve the scene objects witout the fluid objects
        GeometryList filteredSceneGeometries = gatherFilteredSceneObjects(viewPort.getScenes(), fluidGeometryList);

        // create the scene depth texture without the fluid objects
        renderer.setFrameBuffer(sceneDepthBuffer);
        renderer.clearBuffers(true, true, true);
        renderManager.renderGeometryList(filteredSceneGeometries);
        renderer.setFrameBuffer(viewPort.getOutputFrameBuffer());

        displayMap(renderer, dispReflection, 256);
    }

    @Override
    protected void cleanUpFilter(Renderer r) {
        reflectionPass.cleanup(r);
    }

    protected void displayMap(Renderer r, Picture pic, int left) {
        Camera cam = viewPort.getCamera();
        renderManager.setCamera(cam, true);
        int h = cam.getHeight();

        pic.setPosition(left, h / 20f);

        pic.setWidth(128);
        pic.setHeight(128);
        pic.updateGeometricState();
        renderManager.renderGeometry(pic);
        renderManager.setCamera(cam, false);
    }


    public void addFluidGeometry(Geometry fluidGeometry) {
        fluidGeometryList.add(fluidGeometry);
        fluidGeometryList.sort();
    }

    public void clearFluidGeometries() {
        fluidGeometryList.clear();
    }

    public void setFadeColor(ColorRGBA fadeColor) {
        this.fadeColor = fadeColor;
        if (material != null) {
            material.setColor("FadeColor", fadeColor);
        }
    }

    public void setFadeDepth(float fadeDepth) {
        this.fadeDepth = fadeDepth;
        if (material != null) {
            material.setFloat("FadeDepth", fadeDepth);
        }
    }

    public void setShorelineSize(float shorelineSize) {
        this.shorelineSize = shorelineSize;
        if (material != null) {
            material.setFloat("ShorelineSize", shorelineSize);
        }
    }

    public void setShorelineColor(ColorRGBA shorelineColor) {
        this.shorelineColor = shorelineColor;
        if (material != null) {
            material.setColor("ShorelineColor", shorelineColor);
        }
    }

    public void setDistortion(boolean distortion) {
        this.distortion = distortion;
        if (material != null) {
            material.setBoolean("UseDistortion", distortion);
        }
    }

    public void setDistortionStrengthX(float distortionStrengthX) {
        this.distortionStrengthX = distortionStrengthX;
        if (material != null) {
            material.setFloat("DistortionStrengthX", distortionStrengthX);
        }
    }

    public void setDistortionStrengthY(float distortionStrengthY) {
        this.distortionStrengthY = distortionStrengthY;
        if (material != null) {
            material.setFloat("DistortionStrengthY", distortionStrengthY);
        }
    }

    public void setDistortionAmplitudeX(float distortionAmplitudeX) {
        this.distortionAmplitudeX = distortionAmplitudeX;
        if (material != null) {
            material.setFloat("DistortionAmplitudeX", distortionAmplitudeX);
        }
    }

    public void setDistortionAmplitudeY(float distortionAmplitudeY) {
        this.distortionAmplitudeY = distortionAmplitudeY;
        if (material != null) {
            material.setFloat("DistortionAmplitudeY", distortionAmplitudeY);
        }
    }

    public void setDistortionSpeed(float distortionSpeed) {
        this.distortionSpeed = distortionSpeed;
        if (material != null) {
            material.setFloat("DistortionSpeed", distortionSpeed);
        }
    }

    /**
     * Return true when the geometry is in the geometryList.
     */
    private static boolean contains(Geometry geometry, GeometryList geometryList) {
        for (Geometry g : geometryList) {
            if (g.equals(geometry)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Return a new GeometryList containing all the geometries in the scenes, except for the geometries in the
     * fluidGeometries list.
     */
    private static GeometryList gatherFilteredSceneObjects(List<Spatial> scenes, GeometryList fluidGeometries) {
        GeometryList filteredSceneGeometries = new GeometryList(new OpaqueComparator());

        for (Spatial scene : scenes) {
            scene.depthFirstTraversal(new SceneGraphVisitorAdapter() {
                @Override
                public void visit(Geometry geom) {
                    if (!contains(geom, fluidGeometries)) {
                        filteredSceneGeometries.add(geom);
                    }
                }
            });
        }

        return filteredSceneGeometries;
    }

}
