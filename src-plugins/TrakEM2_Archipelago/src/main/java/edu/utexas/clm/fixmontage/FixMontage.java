package edu.utexas.clm.fixmontage;

import ij.IJ;
import ini.trakem2.Project;
import ini.trakem2.display.AreaList;
import ini.trakem2.display.Layer;
import ini.trakem2.display.LayerSet;
import ini.trakem2.display.Patch;
import ini.trakem2.display.Polyline;
import ini.trakem2.display.Profile;
import ini.trakem2.utils.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 *
 */
public class FixMontage
{
    public static class NotReadyException extends RuntimeException
    {
        public NotReadyException()
        {
            super("Not ready yet!");
        }
    }

    private boolean ready;
    // Current traces
    private Project tracesProject;
    // Alignment over photoshop-montaged images
    private Project alignmentProject;
    // Alignment between photoshop montage and original patches
    private Project rectifyProject;
    // Montage over original patches
    private Project montageProject;

    private final ExecutorService service;


    public FixMontage()
    {
        ready = false;

        tracesProject = null;
        alignmentProject = null;
        rectifyProject = null;
        montageProject = null;
        service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

    }

    private void setReady()
    {
        ready = tracesProject != null &&
                alignmentProject != null &&
                rectifyProject != null &&
                montageProject != null;
    }

    public void setTracesProject(final Project project)
    {
        tracesProject = project;

        setReady();
    }

    public void setAlignmentProject(final Project project)
    {
        alignmentProject = project;

        setReady();
    }

    public void setRectifyProject(final Project project)
    {
        rectifyProject = project;

        setReady();
    }

    public void setMontageProject(final Project project)
    {
        montageProject = project;

        setReady();
    }

    public String patchIdentifierFile(final Patch p)
    {
        File f = new File(p.getImageFilePath());
        return f.getName();
    }

    public void fixProjects() throws NotReadyException
    {
        if (!ready)
        {
            throw new NotReadyException();
        }
        else
        {
            final HashMap<String, FixMontageLayerCallable> rawPatchMap =
                    new HashMap<String, FixMontageLayerCallable>();
            final HashMap<String, FixMontageLayerCallable> shopPatchMap =
                    new HashMap<String, FixMontageLayerCallable>();
            final ArrayList<FixMontageLayerCallable> callables =
                    new ArrayList<FixMontageLayerCallable>();
            final ArrayList<Future<FixMontageLayerResult>> futures =
                    new ArrayList<Future<FixMontageLayerResult>>();

            for (final Layer rawLayer : montageProject.getRootLayerSet().getLayers())
            {
                final FixMontageLayerCallable callable = new FixMontageLayerCallable();

                for (final Patch p : getPatches(rawLayer))
                {
                    callable.addMontagePatch(p);
                    rawPatchMap.put(patchIdentifierFile(p), callable);
                    IJ.log("Mapped callable " + callable.getId() + " to " + p.getImageFilePath());
                }

                callables.add(callable);
            }

            for (final Layer rectifyLayer : rectifyProject.getRootLayerSet().getLayers())
            {
                final ArrayList<Patch> rectifyRawPatches = new ArrayList<Patch>();
                final Patch maxPatch = splitPatches(rectifyLayer, rectifyRawPatches);
                FixMontageLayerCallable callable = null;
                for (final Patch p : rectifyRawPatches)
                {
                    if ((callable = rawPatchMap.get(patchIdentifierFile(p))) != null)
                    {
                        IJ.log("Found callable " + callable.getId() + " mapped to " + p.getImageFilePath());
                        break;
                    }
                }

                if (callable != null)
                {
                    callable.setRectifyPatches(rectifyRawPatches, maxPatch);
                    shopPatchMap.put(patchIdentifierFile(maxPatch), callable);
                    IJ.log("Mapped callable " + callable.getId() + " to " + maxPatch.getImageFilePath());
                }
            }

            for (int i = 0; i < alignmentProject.getRootLayerSet().size(); ++i)
            {
                final Layer alignmentLayer = alignmentProject.getRootLayerSet().getLayer(i);
                final Layer tracesLayer = tracesProject.getRootLayerSet().getLayer(i);

                final Patch alignmentPatch = getPatch(alignmentLayer);
                final FixMontageLayerCallable callable;

                if (alignmentPatch != null &&
                        ((callable = shopPatchMap.get(patchIdentifierFile(alignmentPatch))) != null))
                {
                    callable.setAlignmentPatch(alignmentPatch);
                    callable.setTracesPatch(getPatch(tracesLayer));
                }
                else if (alignmentPatch != null)
                {
                    IJ.log("Found no match for patch " + alignmentPatch.getImageFilePath());
                }
            }

            for (final FixMontageLayerCallable callable : callables)
            {
                futures.add(service.submit(callable));
            }

            try
            {
                applyResults(futures);
            }
            catch (Exception e)
            {
                e.printStackTrace();
                IJ.error("Error while processing: " + e);
            }
        }
    }

    private void applyResults(final List<Future<FixMontageLayerResult>> futures)
            throws ExecutionException, InterruptedException
    {
        final List<AreaList> origAreaLists = getAreaLists(tracesProject);
        final List<Polyline> origPolylines = getPolylines(tracesProject);

        final HashMap<Long, AreaList> fixedAreaLists = new HashMap<Long, AreaList>();
        final HashMap<Long, Polyline> fixedPolylines = new HashMap<Long, Polyline>();
        final HashSet<Long> idsToAdd = new HashSet<Long>();

        int count = 0;

        IJ.log("Creating new objects...");

        for (final AreaList areaList : origAreaLists)
        {
            final AreaList fixedAreaList = new AreaList(montageProject, areaList.getTitle(), 0, 0);
            fixedAreaList.setAlpha(areaList.getAlpha());
            //fixedAreaList.setColor(areaList.getColor());
            fixedAreaList.setVisible(areaList.isVisible());

            fixedAreaLists.put(areaList.getId(), fixedAreaList);
        }

        for (final Polyline polyline : origPolylines)
        {
            final Polyline fixedPolyline = new Polyline(montageProject, polyline.getTitle());
            fixedPolyline.setAlpha(polyline.getAlpha());
            fixedPolyline.setVisible(polyline.isVisible());
            //fixedPolyline.setColor(polyline.getColor());

            fixedPolylines.put(polyline.getId(), fixedPolyline);
        }

        for (final Future<FixMontageLayerResult> future : futures)
        {
            final FixMontageLayerResult result = future.get();
            if (result != null)
            {
                LayerSet rls = tracesProject.getRootLayerSet();
                long id = result.getTracesLayerId();
                Layer l = rls.getLayer(id);
                final List<Profile> profiles = getProfiles(l);
                final Layer montageLayer =
                        montageProject.getRootLayerSet().getLayer(result.getMontageLayerId());

                ++count;

                IJ.log("Fixed objects in layer " + count + " of " + futures.size() +
                        ", applying to new project");

                for (final long key : fixedPolylines.keySet())
                {
                    if (result.apply(fixedPolylines.get(key), key))
                    {
                        idsToAdd.add(fixedPolylines.get(key).getId());
                    }
                }

                for (final long key : fixedAreaLists.keySet())
                {
                    if (result.apply(fixedAreaLists.get(key), key))
                    {
                        idsToAdd.add(fixedAreaLists.get(key).getId());
                    }
                }

                for (final Profile profile : profiles)
                {
                    result.insertProfile(montageLayer, profile);
                }
            }
        }

        IJ.log("Adding area lists to project...");
        for (final AreaList areaList : fixedAreaLists.values())
        {
            if (idsToAdd.contains(areaList.getId()))
            {
                montageProject.getRootLayerSet().add(areaList);
            }
        }

        IJ.log("Adding Z-traces to project...");

        for (final Polyline polyline : fixedPolylines.values())
        {
            if (idsToAdd.contains(polyline.getId()))
            {
                montageProject.getRootLayerSet().add(polyline);
            }
        }

        IJ.log("OK. I think we're done");
    }



    public static List<Patch> getPatches(final Layer l)
    {
        return Utils.castCollection(
                l.getDisplayables(Patch.class, true),
                Patch.class, true);
    }

    public static List<AreaList> getAreaLists(final Project p)
    {
        return Utils.castCollection(
                p.getRootLayerSet().getZDisplayables(AreaList.class, true),
                AreaList.class, true);
    }

    public static List<Polyline> getPolylines(final Project p)
    {
        return Utils.castCollection(
                p.getRootLayerSet().getZDisplayables(Polyline.class, true),
                Polyline.class, true);
    }


    public static List<Profile> getProfiles(final Layer l)
    {
        return Utils.castCollection(
                l.getDisplayables(Profile.class, true),
                Profile.class, true);
    }

    public static Patch getPatch(final Layer l)
    {
        final List<Patch> patches = getPatches(l);
        return patches.size() == 0 ? null : patches.get(0);
    }

    public static Patch splitPatches(final Layer layer, final List<Patch> patches)
    {
        final List<Patch> layerPatches = Utils.castCollection(
                layer.getDisplayables(Patch.class, true),
                Patch.class, true);
        Patch maxPatch = null;

        for (final Patch p : layerPatches)
        {
            if (maxPatch == null ||
                    p.getHeight() * p.getWidth() > maxPatch.getHeight() * maxPatch.getWidth())
            {
                maxPatch = p;
            }
        }

        layerPatches.remove(maxPatch);
        patches.addAll(layerPatches);

        return maxPatch;
    }

}
