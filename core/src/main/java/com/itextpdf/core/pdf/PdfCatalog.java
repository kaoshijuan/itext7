package com.itextpdf.core.pdf;

import com.itextpdf.basics.PdfException;
import com.itextpdf.core.pdf.action.PdfAction;
import com.itextpdf.core.pdf.layer.PdfOCProperties;
import com.itextpdf.core.pdf.navigation.PdfDestination;

import java.util.*;

public class PdfCatalog extends PdfObjectWrapper<PdfDictionary> {

    protected final PdfPagesTree pageTree;
    protected PdfOCProperties ocProperties;

    private final static String OutlineRoot = "Outlines";
    private PdfOutline outlines;
    private boolean replaceNamedDestinations = true;
    private HashMap<PdfObject, ArrayList<PdfOutline>> pagesWithOutlines = new HashMap<PdfObject, ArrayList<PdfOutline>>();

    protected PdfCatalog(PdfDictionary pdfObject, PdfDocument pdfDocument) throws PdfException {
        super(pdfObject);
        if (pdfObject == null) {
            throw new PdfException(PdfException.DocumentHasNoCatalogObject);
        }
        getPdfObject().makeIndirect(pdfDocument);
        getPdfObject().put(PdfName.Type, PdfName.Catalog);
        pageTree = new PdfPagesTree(this);
    }

    protected PdfCatalog(PdfDocument pdfDocument) throws PdfException {
        this(new PdfDictionary(), pdfDocument);
    }

    public void addPage(PdfPage page) throws PdfException {
        if (page.isFlushed())
            throw new PdfException(PdfException.FlushedPageCannotBeAddedOrInserted, page);
        if (page.getDocument() != getDocument())
            throw new PdfException(PdfException.Page1CannotBeAddedToDocument2BecauseItBelongsToDocument3).setMessageParams(page, getDocument(), page.getDocument());
        pageTree.addPage(page);
    }

    public void addPage(int index, PdfPage page) throws PdfException {
        if (page.isFlushed())
            throw new PdfException(PdfException.FlushedPageCannotBeAddedOrInserted, page);
        if (page.getDocument() != getDocument())
            throw new PdfException(PdfException.Page1CannotBeAddedToDocument2BecauseItBelongsToDocument3).setMessageParams(page, getDocument(), page.getDocument());
        pageTree.addPage(index, page);
    }

    public PdfPage getPage(int pageNum) throws PdfException {
        return pageTree.getPage(pageNum);
    }

    public int getNumOfPages() {
        return pageTree.getNumOfPages();
    }

    public int getPageNum(PdfPage page) {
        return pageTree.getPageNum(page);
    }

    public boolean removePage(PdfPage page) throws PdfException {
        //TODO log removing flushed page
        return pageTree.removePage(page);
    }

    public PdfPage removePage(int pageNum) throws PdfException {
        //TODO log removing flushed page
        return pageTree.removePage(pageNum);
    }

    /**
     * Use this method to get the <B>Optional Content Properties Dictionary</B>.
     * Note that if you call this method, then the PdfDictionary with OCProperties will be
     * generated from PdfOCProperties object right before closing the PdfDocument,
     * so if you want to make low-level changes in Pdf structures themselves (PdfArray, PdfDictionary, etc),
     * then you should address directly those objects, e.g.:
     * <CODE>
     * PdfCatalog pdfCatalog = pdfDoc.getCatalog();
     * PdfDictionary ocProps = pdfCatalog.getAsDictionary(PdfName.OCProperties);
     * // manipulate with ocProps.
     * </CODE>
     * Also note that this method is implicitly called when creating a new PdfLayer instance,
     * so you should either use hi-level logic of operating with layers,
     * or manipulate low-level Pdf objects by yourself.
     *
     * @param createIfNotExists true to create new /OCProperties entry in catalog if not exists,
     *                          false to return null if /OCProperties entry in catalog is not present.
     * @return the Optional Content Properties Dictionary
     */
    public PdfOCProperties getOCProperties(boolean createIfNotExists) throws PdfException {
        if (ocProperties != null)
            return ocProperties;
        else {
            PdfDictionary ocPropertiesDict = getPdfObject().getAsDictionary(PdfName.OCProperties);
            if (ocPropertiesDict != null) {
                ocProperties = new PdfOCProperties(ocPropertiesDict, getDocument());
            } else if (createIfNotExists) {
                ocProperties = new PdfOCProperties(new PdfDictionary(), getDocument());
            }
        }
        return ocProperties;
    }

    /**
     * PdfCatalog will be flushed in PdfDocument.close(). User mustn't flush PdfCatalog!
     */
    @Override
    public void flush() throws PdfException {
        throw new PdfException(PdfException.YouCannotFlushPdfCatalogManually);
    }

    public PdfCatalog setOpenAction(PdfDestination destination) {
        return put(PdfName.OpenAction, destination);
    }

    public PdfCatalog setOpenAction(PdfAction action) {
        return put(PdfName.OpenAction, action);
    }

    public PdfCatalog setAdditionalAction(PdfName key, PdfAction action) throws PdfException {
        PdfAction.setAdditionalAction(this, key, action);
        return this;
    }

    public boolean isReplaceNamedDestinations() {
        return replaceNamedDestinations;
    }

    public void setReplaceNamedDestinations(boolean replaceNamedDestinations) {
        this.replaceNamedDestinations = replaceNamedDestinations;
    }

    public HashMap<PdfObject, ArrayList<PdfOutline>> getPagesWithOutlines() {
        return pagesWithOutlines;
    }

    /**
     * True indicates that getOCProperties() was called, may have been modified,
     * and thus its dictionary needs to be reconstructed.
     */
    protected boolean isOCPropertiesMayHaveChanged() {
        return ocProperties != null;
    }

    PdfOutline getOutlines(boolean updateOutlines) throws PdfException {

        if (outlines!= null && !updateOutlines)
            return outlines;
        if (outlines != null)
            outlines.clear();

        HashMap<Object, PdfObject> names = getNamedDestinations();
        PdfDictionary outlineRoot = getPdfObject().getAsDictionary(PdfName.Outlines);
        if (outlineRoot == null){
            return null;
        }

        outlines = new PdfOutline(OutlineRoot, outlineRoot, null);
        getNextItem(outlineRoot.getAsDictionary(PdfName.First), outlines, names);

        return outlines;
    }

    private void addOutlineToPage(PdfOutline outline, HashMap<Object, PdfObject> names) throws PdfException {

        PdfObject obj = outline.getDestination().getDestinationPage(names);
        ArrayList<PdfOutline> outs = pagesWithOutlines.get(obj);
        if (outs == null) {
            outs = new ArrayList<PdfOutline>();
            pagesWithOutlines.put(obj, outs);
        }
        outs.add(outline);
    }

    private void getNextItem(PdfDictionary item, PdfOutline parent, HashMap<Object, PdfObject> names) throws PdfException {

        PdfOutline outline = new PdfOutline(item.getAsString(PdfName.Title).toUnicodeString(), item, parent);
        PdfObject dest = item.get(PdfName.Dest);
        if (dest != null) {
            PdfDestination destination = PdfDestination.makeDestination(dest);
            outline.setDestination(destination);
            if (replaceNamedDestinations){
                destination.replaceNamedDestination(names);
            }
            addOutlineToPage(outline, names);
        }
        parent.addChild(outline);

        PdfDictionary processItem = item.getAsDictionary(PdfName.First);
        if (processItem != null){
            getNextItem(processItem, outline, names);
        }
        processItem = item.getAsDictionary(PdfName.Next);
        if (processItem != null){
            getNextItem(processItem, parent, names);
        }
    }

    private HashMap<Object, PdfObject> getNamedDestinations() throws PdfException {
        HashMap<Object, PdfObject> names = getNamedDestinationsFromNames();
        names.putAll(getNamedDestinationsFromStrings());
        return names;
    }

    private HashMap<Object, PdfObject> getNamedDestinationsFromNames() throws PdfException {
        HashMap<Object, PdfObject> names = new HashMap<Object, PdfObject>();
        PdfDictionary destinations = getDocument().getCatalog().getPdfObject().getAsDictionary(PdfName.Dests);
        if(destinations != null){
            Set<PdfName> keys = destinations.keySet();
            for (PdfName key : keys){
                PdfArray array = getNameArray(destinations.get(key));
                if (array == null){
                    continue;
                }
                names.put(key, array);
            }
            return names;
        }
        return names;
    }

    private HashMap<String, PdfObject> getNamedDestinationsFromStrings() throws PdfException {
        PdfDictionary dictionary = getDocument().getCatalog().getPdfObject().getAsDictionary(PdfName.Names);
        if(dictionary != null){
            dictionary = dictionary.getAsDictionary(PdfName.Dests);
            if (dictionary != null){
                HashMap<String, PdfObject> names = readTree(dictionary);
                for (Iterator<Map.Entry<String, PdfObject>> it = names.entrySet().iterator(); it.hasNext();) {
                    Map.Entry<String, PdfObject> entry = it.next();
                    PdfArray arr = getNameArray(entry.getValue());
                    if (arr != null)
                        entry.setValue(arr);
                    else
                        it.remove();
                }
                return names;
            }
        }

        return new HashMap<String, PdfObject>();
    }

    private HashMap<String, PdfObject> readTree(PdfDictionary dictionary) throws PdfException {
        HashMap<String, PdfObject> items = new HashMap<String, PdfObject>();
        if (dictionary != null){
            iterateItems(dictionary, items, null);
        }
        return items;
    }

    private PdfString iterateItems(PdfDictionary dictionary, HashMap<String, PdfObject> items, PdfString leftOver) throws PdfException {
        PdfArray names = dictionary.getAsArray(PdfName.Names);
        if (names != null){
            for (int k = 0; k < names.size(); k++){
                PdfString name;
                if (leftOver == null)
                    name = names.getAsString(k++);
                else {
                    name = leftOver;
                    leftOver = null;
                }
                if(k < names.size()){
                    items.put(name.toUnicodeString(), names.get(k));
                }
                else {
                    return name;
                }
            }
        } else if ((names = dictionary.getAsArray(PdfName.Kids)) != null){
            for (int k = 0; k < names.size(); k++){
                PdfDictionary kid = names.getAsDictionary(k);
                leftOver = iterateItems(kid, items, leftOver);
            }
        }
        return null;
    }

    private PdfArray getNameArray(PdfObject obj) throws PdfException {
        if(obj == null)
            return null;
        if (obj.isArray())
            return (PdfArray)obj;
        else if (obj.isDictionary()) {
            PdfArray arr = ((PdfDictionary)obj).getAsArray(PdfName.D);
            return arr;
        }
        return null;
    }
}
