/**
 * Script for Qupath software 
 * I made that script to improve the segmentation accuracy  (especially the cell expansion)of fluorescence images of cells cytospinned on slides. 
 * It is a script to delimit the segmentation and cell expansion to an ROI made with a pixel classifier on Qupath (so that it does not include the area next to the cells, which does not contain cells, and so with very low pixel values that may skew the MFIs). Compatible QuPath 0.7.0
 * 
 * Before using the script : 
 * 0) Create a project
 * 1) Load the image
 * 2) In the section "Annotation" create a new class list named "cell"
 * 3) With the brush tool draw regions corresponding to where there are cells, each region will be displayed  in the "annotation list " section. In the "Class list" click on the class you just created ("cell"), then select all the regions you just drawn in the section "Annotation list" and press on "Set selected", so that all the regions corresponding of cells are now associated to the class "cell"
 * 4) In the section "Annotation" create a new class list named "background"
 * 5) With the brush tool draw regions corresponding to where there are NO cells, each region will be displayed in the "annotation list " section. In the "Class list" click on the class you just created ("background"), then select all the regions you just drawn in the section "Annotation list" and press on "Set selected", so that all the regions corresponding of area with no cells are now associated to the class "background"
 * 6) Press on Classify > Pixel Classification > train pixel classifier
 * 7) You can set your favorite classifier algorithm (I usually choose "Random trees"), the resolution (I usually choose "Full"), the features (basically the number of channel you want to take into account for the classificatio, usually DAPI + a cytoplamic marker such a cell mask), the Output (for our purpose : Classification), Region (Everywhere), press "Live prediction"
 * 8) check visually that you are happy with the classification
 * 9) once you're happy, enter a classifier name (write the following name "cell_background", it will be important for next steps)
 * 10) Press "create objects", set the full image as the parent objects and press OK. The new object type must be "annotation", you can set the minimum object size and the minimum hold size (depends on you type of cell), and you can tick the boxes : "Split objects" and "Delete existing objects"
 * 11) Now there are new annotations adequate to you classification, you can double check that you are happy with the classification visually
 * 12) you can now run the script (just press "Run"), it will perform the segmentation only in the ROI classified as "cell". You can adjust the parameters of segmentation and cell expansion line 42, adjust them to until you are happy with the segmentaiton. 
 * 13) Once the (i) the pixel classifier gave you good classification, (ii) the segmentation settings (line 42) gave you good classification, you can run the Script "Segmentation_after_classification", available in my repository (while making sure you reported the segmentation settings line 42), it will run that segmentation with the trained pixel classifier "cell_background" in each image of a given project and will upload you an .csv file with measures (MFI, area size, max, min...) for each cell. For example, if you took 50 images of a same slide, and you want to get the cytoplasm MFI for each cell of your 50 images, you can train the pixel classifier in only one image, set the segmentaiton settings for only one image, and run it for the whole project, you get you results in a few seconds/minutes. 
 *  */

import qupath.lib.roi.GeometryTools
import qupath.lib.objects.PathObjects
import javafx.application.Platform

// 1. Get the active image
def imageData = getCurrentImageData()
if (imageData == null) {
    print "Error : Open an image !"
    return
}

setImageType('FLUORESCENCE')

// 2. Remove old seelction before starting
removeDetections()

// 3. Select "cell" classes
selectObjectsByClassification("cell")

// 4. Start cell detection (segmentation): 
runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage":"DAPI","requestedPixelSizeMicrons":0.5,"backgroundRadiusMicrons":8.0,"backgroundByReconstruction":true,"medianRadiusMicrons":0.0,"sigmaMicrons":1.5,"minAreaMicrons":35.0,"maxAreaMicrons":900.0,"threshold":2500.0,"watershedPostProcess":true,"cellExpansionMicrons":20,"includeNuclei":true,"smoothBoundaries":true,"makeMeasurements":true}') 

// 5. Adjust the cell expansion to the "cell" ROI
def annotationsJaunes = getAnnotationObjects().findAll { it.getPathClass() == getPathClass("cell") }
def allCells = getCellObjects()
def cellsModifiees = []

for (annotation in annotationsJaunes) {
    def annotationGeom = annotation.getROI().getGeometry()
    def plane = annotation.getROI().getImagePlane()
    
    for (cell in allCells) {
        // Si la cellule appartient à cette annotation jaune
        if (annotation.getROI().contains(cell.getROI().getCentroidX(), cell.getROI().getCentroidY())) {
            
            def cellGeom = cell.getROI().getGeometry()
            // On force l'intersection : la cellule ne gardera que ce qui est AUSSI dans le jaune
            def intersectedGeom = cellGeom.intersection(annotationGeom)
            
            if (intersectedGeom != null && !intersectedGeom.isEmpty()) {
                def newCellROI = GeometryTools.geometryToROI(intersectedGeom, plane)
                def nucleusROI = cell.getNucleusROI() // On préserve le noyau intact
                
                // On recrée l'objet cellule proprement avec sa nouvelle frontière rouge
                def reconstructedCell = PathObjects.createCellObject(newCellROI, nucleusROI, cell.getPathClass(), cell.getMeasurementList())
                cellsModifiees.add(reconstructedCell)
            }
        }
    }
}


removeObjects(allCells, true)
addObjects(cellsModifiees)
fireHierarchyUpdate()


println("Finished ! You can check visually whether you are happy with the segmentation")

