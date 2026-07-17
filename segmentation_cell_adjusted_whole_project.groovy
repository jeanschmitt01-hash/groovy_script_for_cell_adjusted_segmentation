 /**
 * This script has been auto-generated from the command history in QuPath 0.7.0.
 *
 * I completed this script: it automatized the analysis for each image : segmentation of the cells based on DAPI extend the area of 20µm to get a pseudo cytplasm region, measure fluorescence values of cytplasm and nucleus, and save a .csv file for each image with a number of lines = to the number of cell detected. --> you can then merge all these dataset of R, Python, whateven...
 *  - You may need to edit the script before applying it to new images.
 *  - You should not run scripts you don't understand or from untrusted sources.
 *
 * For information about citing QuPath in a paper, see "Help > Citing QuPath in a paper (web)".
 * 
 * 
 * --> IMPORTANT TO READ BEFORE USE : 
 * THIS SCRIPT REQUIRES TO FIRST PERFORM A "TRAIN PIXEL CLASSIFIER" with two class trained, a class "cell" trained with manual annotation of cells (nucleus and cytoplasm included, with only two channels in "features" for the training : DAPI and the channel corresponding to the Cell mask), and a class "background" trained with a manual annotation of background. Once the model is trained and is satisfactory, save it as "cell_background" --> the model should be able to draw a clear barriere between cells and background. You can adjust the  Minimum object size (µm²) and Minimum hold size (µm²) in this line : "createAnnotationsFromPixelClassifier", that's respectively the 2nd and 3rd arguments
 * 
 * /!\ BEFORE running this script, make sure that (i) the classification is correct, (ii)these arguments : "minAreaMicrons":40.0,"maxAreaMicrons":900.0,"threshold":5000.0,  work with your images (it may vary according to the DAPI dillution, cell mask dillution, the time of exposure, laser intensity...) --> just make sure it performs the segmentation correctly. To make the verification, perform the classification (so now you will have lot of annotation of cells), and run teh script "Script groovy pour dégager l'expansion du background" --> you will be able to see visually the result of the segmentation, then you can copy and paste the arguments to this script
 */
import qupath.lib.roi.GeometryTools
import qupath.lib.objects.PathObjects
import javafx.application.Platform

def project = getProject() 
// access to the project (list of images, metadata, etc.)
// but NOT the image pixels themselves

// Output folder (inside project or external path)
def outputFile = buildFilePath(PROJECT_BASE_DIR, "all_measurements.csv")


for (entry in project.getImageList()) {  
    // Loop over all images in the project

    print "Processing: " + entry.getImageName()
    
    def imageData = entry.readImageData()  //load an image from the entry, which refers to a list of image
    
    setBatchProjectAndImage(project, imageData)
    // IMPORTANT: tells QuPath which image is currently active in batch mode
    
    
    setImageType('FLUORESCENCE')
    // Define image type (affects channel handling)
    print imageData.getServer().getWidth() //display the number of pixel in the Width
    print imageData.getServer().getHeight()//display the number of pixel in the Width --> just to be sure the image is actually loaded in the loop
    
    clearDetections()
    clearAnnotations()
    resetSelection()
    // Avoid accumulation of objects between images (VERY important in batch)
    
    createAnnotationsFromPixelClassifier("cell_background", 70.0, 70.0, "SPLIT", "DELETE_EXISTING") 
    // use the trained model to identify two areas : "cell" and "background"
    
    selectObjectsByClassification("cell");
    // select annotation classified as "cell"
    runPlugin('qupath.imagej.detect.cells.WatershedCellDetection', '{"detectionImage":"DAPI","requestedPixelSizeMicrons":0.5,"backgroundRadiusMicrons":8.0,"backgroundByReconstruction":true,"medianRadiusMicrons":0.0,"sigmaMicrons":1.5,"minAreaMicrons":35.0,"maxAreaMicrons":900.0,"threshold":2500.0,"watershedPostProcess":true,"cellExpansionMicrons":20,"includeNuclei":true,"smoothBoundaries":true,"makeMeasurements":true}') // with "makeMeasurment":true Qupath calcul every variable available (there are a lot)
    
   // normally at that point, segmentation has been performed within the "cell" annotations (please, feel free to adjust the size of "cellExpansionMicrons"), if now we must crop the part of the segmentation overlaying with the background (to not skew the MFI): 
   


        
    // 5. SCULPTAGE DES CELLULES (Le rouge s'aligne sur le jaune)
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

// Remplacement des anciennes cellules par les versions rabotées
    removeObjects(allCells, true)
    addObjects(cellsModifiees)
    fireHierarchyUpdate()
    // -----------------------------
    // EXPORT PER IMAGE 
    // -----------------------------

    def name = entry.getImageName().replaceAll("\\W+", "_") // clean the name 
    def outputPath = buildFilePath(PROJECT_BASE_DIR, name + "_measurements.csv")  //create .csv file to store the data of the image 
    print outputPath
    //'Nucleus: Cy5 mean', 'Nucleus: Cy5 sum', 'Nucleus: Cy5 std dev', 'Nucleus: Cy5 max', 'Nucleus: Cy5 min', 'Nucleus: Cy5 range', 'Nucleus: GFP mean', 'Nucleus: GFP sum', 'Nucleus: GFP std dev', 'Nucleus: GFP max', 'Nucleus: GFP min', 'Nucleus: GFP range' 'Cell: Cy5 mean', 'Cell: Cy5 std dev', 'Cell: Cy5 max', 'Cell: Cy5 min', 'Cell: GFP mean', 'Cell: GFP std dev', 'Cell: GFP max', 'Cell: GFP min', 'Cytoplasm: Cy5 mean', 'Cytoplasm: Cy5 std dev', 'Cytoplasm: Cy5 max', 'Cytoplasm: Cy5 min', 'Cytoplasm: GFP mean', 'Cytoplasm: GFP std dev', 'Cytoplasm: GFP max', 'Cytoplasm: GFP min'
    saveDetectionMeasurements(outputPath,  'Image', 'Object type', 'Object ID', 'Parent', 'ROI', 'Centroid X µm', 'Centroid Y µm', 'Nucleus: Area', 'Nucleus: Perimeter', 'Nucleus: Circularity', 'Nucleus: Max caliper', 'Nucleus: Min caliper', 'Nucleus: Eccentricity', 'Nucleus: DAPI mean', 'Nucleus: DAPI sum', 'Nucleus: DAPI std dev', 'Nucleus: DAPI max', 'Nucleus: DAPI min', 'Nucleus: DAPI range', 'Nucleus: Cy5 bis mean', 'Nucleus: Cy5 bis sum', 'Nucleus: Cy5 bis std dev', 'Nucleus: Cy5 bis max', 'Nucleus: Cy5 bis min', 'Nucleus: Cy5 bis range', 'Nucleus: Texas Red mean', 'Nucleus: Texas Red sum', 'Nucleus: Texas Red std dev', 'Nucleus: Texas Red max', 'Nucleus: Texas Red min', 'Nucleus: Texas Red range', 'Cell: Area', 'Cell: Perimeter', 'Cell: Circularity', 'Cell: Max caliper', 'Cell: Min caliper', 'Cell: Eccentricity', 'Cell: DAPI mean', 'Cell: DAPI std dev', 'Cell: DAPI max', 'Cell: DAPI min', 'Cell: Cy5 bis mean', 'Cell: Cy5 bis std dev', 'Cell: Cy5 bis max', 'Cell: Cy5 bis min', 'Cell: Texas Red mean', 'Cell: Texas Red std dev', 'Cell: Texas Red max', 'Cell: Texas Red min', 'Cytoplasm: DAPI mean', 'Cytoplasm: DAPI std dev', 'Cytoplasm: DAPI max', 'Cytoplasm: DAPI min', 'Cytoplasm: Cy5 bis mean', 'Cytoplasm: Cy5 bis std dev', 'Cytoplasm: Cy5 bis max', 'Cytoplasm: Cy5 bis min', 'Cytoplasm: Texas Red mean', 'Cytoplasm: Texas Red std dev', 'Cytoplasm: Texas Red max', 'Cytoplasm: Texas Red min', 'Nucleus/Cell area ratio')
    println "Saved measurements to: " + outputPath  //check print
}
print "ALL DONE"
   
   
   