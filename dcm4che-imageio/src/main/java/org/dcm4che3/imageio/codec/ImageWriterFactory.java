/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/gunterze/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Agfa Healthcare.
 * Portions created by the Initial Developer are Copyright (C) 2013
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che3.imageio.codec;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.dcm4che3.conf.core.api.ConfigurableClass;
import org.dcm4che3.conf.core.api.ConfigurableProperty;
import org.dcm4che3.conf.core.api.LDAP;
import org.dcm4che3.imageio.codec.jpeg.PatchJPEGLS;
import org.dcm4che3.util.Property;
import org.dcm4che3.util.ResourceLocator;
import org.dcm4che3.util.SafeClose;
import org.dcm4che3.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * 
 */
@LDAP(objectClasses = "dcmImageWriterFactory")
@ConfigurableClass
public class ImageWriterFactory implements Serializable {

    private static final Logger LOG = LoggerFactory.getLogger(ImageReaderFactory.class);

    private static final long serialVersionUID = 6328126996969794374L;

    @LDAP(objectClasses = "dcmImageWriter")
    @ConfigurableClass
    public static class ImageWriterParam implements Serializable {

        private static final long serialVersionUID = 3521737269113651910L;

        @ConfigurableProperty(name = "dcmIIOFormatName")
        public String formatName;

        @ConfigurableProperty(name = "dcmJavaClassName")
        public String className;

        @ConfigurableProperty(name = "dcmPatchJPEGLS")
        public PatchJPEGLS patchJPEGLS;

        @ConfigurableProperty(name = "dcmImageWriteParam")
        public Property[] imageWriteParams;

        @ConfigurableProperty(name = "dcmWriteIIOMetadata")
        public Property[] iioMetadata;

        private String name;

        public ImageWriterParam() {
        }

        public ImageWriterParam(String formatName, String className, PatchJPEGLS patchJPEGLS,
            Property[] imageWriteParams, Property[] iioMetadata, String name) {
            if (formatName == null || name == null)
                throw new IllegalArgumentException();
            this.formatName = formatName;
            this.className = nullify(className);
            this.patchJPEGLS = patchJPEGLS;
            this.imageWriteParams = imageWriteParams;
            this.iioMetadata = iioMetadata;
            this.name = name;
        }

        public ImageWriterParam(String formatName, String className, String patchJPEGLS, String[] imageWriteParams,
            String[] iioMetadata, String name) {
            this(formatName, className, patchJPEGLS != null && !patchJPEGLS.isEmpty() ? PatchJPEGLS
                .valueOf(patchJPEGLS) : null, Property.valueOf(imageWriteParams), Property.valueOf(iioMetadata), name);
        }

        public ImageWriterParam(String formatName, String className, String patchJPEGLS, String[] imageWriteParams,
            String name) {
            this(formatName, className, patchJPEGLS != null && !patchJPEGLS.isEmpty() ? PatchJPEGLS
                .valueOf(patchJPEGLS) : null, Property.valueOf(imageWriteParams), null, name);
        }

        public Property[] getImageWriteParams() {
            return imageWriteParams;
        }

        public Property[] getIIOMetadata() {
            return iioMetadata;
        }

        public String getFormatName() {
            return formatName;
        }

        public void setFormatName(String formatName) {
            this.formatName = formatName;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public PatchJPEGLS getPatchJPEGLS() {
            return patchJPEGLS;
        }

        public void setPatchJPEGLS(PatchJPEGLS patchJPEGLS) {
            this.patchJPEGLS = patchJPEGLS;
        }

        public void setImageWriteParams(Property[] imageWriteParams) {
            this.imageWriteParams = imageWriteParams;
        }

        public Property[] getIioMetadata() {
            return iioMetadata;
        }

        public void setIioMetadata(Property[] iioMetadata) {
            this.iioMetadata = iioMetadata;
        }

        public String tosString() {
            return name;
        }
    }

    public static class ImageWriterItem {

        private final ImageWriter imageWriter;
        private final ImageWriterParam imageWriterParam;

        public ImageWriterItem(ImageWriter imageReader, ImageWriterParam imageReaderParam) {
            this.imageWriter = imageReader;
            this.imageWriterParam = imageReaderParam;
        }

        public ImageWriter getImageWriter() {
            return imageWriter;
        }

        public ImageWriterParam getImageWriterParam() {
            return imageWriterParam;
        }
    }

    private static volatile ImageWriterFactory defaultFactory;

    @LDAP(distinguishingField = "dicomTransferSyntax", noContainerNode = true)
    @ConfigurableProperty(
        name="dicomImageWriterMap",
        label = "Image Writers",
        description = "Image writers by transfer syntaxes"
    )
    private Map<String, List<ImageWriterParam>> map = new LinkedHashMap<String, List<ImageWriterParam>>();

    private static String nullify(String s) {
        return s == null || s.isEmpty() || s.equals("*") ? null : s;
    }

    public Map<String, List<ImageWriterParam>> getMap() {
        return map;
    }

    public void setMap(Map<String, List<ImageWriterParam>> map) {
        this.map = map;
    }

    public static ImageWriterFactory getDefault() {
        if (defaultFactory == null)
            defaultFactory = initDefault();

        return defaultFactory;
    }

    public static void resetDefault() {
        defaultFactory = null;
    }

    public static void setDefault(ImageWriterFactory factory) {
        if (factory == null)
            throw new NullPointerException();

        defaultFactory = factory;
    }

    private static ImageWriterFactory initDefault() {
        ImageWriterFactory factory = new ImageWriterFactory();
        String name =
            System.getProperty(ImageWriterFactory.class.getName(), "org/dcm4che3/imageio/codec/ImageWriterFactory.xml");
        try {
            factory.load(name);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load Image Writer Factory configuration from: " + name, e);
        }
        return factory;
    }

    public void load(InputStream stream) throws IOException {
        XMLStreamReader xmler = null;
        try {
            XMLInputFactory xmlif = XMLInputFactory.newInstance();
            xmler = xmlif.createXMLStreamReader(stream);

            int eventType;
            while (xmler.hasNext()) {
                eventType = xmler.next();
                switch (eventType) {
                    case XMLStreamConstants.START_ELEMENT:
                        String key = xmler.getName().getLocalPart();
                        if ("ImageWriterFactory".equals(key)) {
                            while (xmler.hasNext()) {
                                eventType = xmler.next();
                                switch (eventType) {
                                    case XMLStreamConstants.START_ELEMENT:
                                        key = xmler.getName().getLocalPart();
                                        if ("element".equals(key)) {
                                            String tsuid = xmler.getAttributeValue(null, "tsuid");

                                            boolean state = true;
                                            while (xmler.hasNext() && state) {
                                                eventType = xmler.next();
                                                switch (eventType) {
                                                    case XMLStreamConstants.START_ELEMENT:
                                                        key = xmler.getName().getLocalPart();
                                                        if ("writer".equals(key)) {
                                                            ImageWriterParam param =
                                                                new ImageWriterParam(xmler.getAttributeValue(null,
                                                                    "format"), xmler.getAttributeValue(null, "class"),
                                                                    xmler.getAttributeValue(null, "patchJPEGLS"),
                                                                    StringUtils.split(
                                                                        xmler.getAttributeValue(null, "params"), ';'),
                                                                    xmler.getAttributeValue(null, "name"));
                                                            put(tsuid, param);
                                                        }
                                                        break;
                                                    case XMLStreamConstants.END_ELEMENT:
                                                        if ("element".equals(xmler.getName().getLocalPart())) {
                                                            state = false;
                                                        }
                                                        break;
                                                    default:
                                                        break;
                                                }
                                            }
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        catch (XMLStreamException e) {
            LOG.error("Cannot read DICOM Writers! " + e.getMessage());
        } finally {
            if (xmler != null) {
                try {
                    xmler.close();
                } catch (XMLStreamException e) {
                    LOG.debug(e.getMessage());
                }
            }
            SafeClose.close(stream);
        }
    }

    public void load(String name) throws IOException {
        URL url;
        try {
            url = new URL(name);
        } catch (MalformedURLException e) {
            url = ResourceLocator.getResourceURL(name, this.getClass());
            if (url == null)
                throw new IOException("No such resource: " + name);
        }
        InputStream in = url.openStream();
        try {
            load(in);
        } finally {
            SafeClose.close(in);
        }
    }

    public List<ImageWriterParam> get(String tsuid) {
        return map.get(tsuid);
    }

    public boolean contains(String tsuid) {
        return map.containsKey(tsuid);
    }

    public boolean put(String tsuid, ImageWriterParam param) {
        List<ImageWriterParam> writerSet = get(tsuid);
        if (writerSet == null) {
            writerSet = new ArrayList<ImageWriterParam>();
            map.put(tsuid, writerSet);
        }
        return writerSet.add(param);
    }

    public List<ImageWriterParam> remove(String tsuid) {
        return map.remove(tsuid);
    }

    public Set<Entry<String, List<ImageWriterParam>>> getEntries() {
        return Collections.unmodifiableMap(map).entrySet();
    }

    public void clear() {
        map.clear();
    }

    public static ImageWriterItem getImageWriterParam(String tsuid) {
        List<ImageWriterParam> list = getDefault().get(tsuid);
        if (list != null) {
            synchronized (list) {
                for (Iterator<ImageWriterParam> it = list.iterator(); it.hasNext();) {
                    ImageWriterParam imageParam = it.next();
                    String cl = imageParam.getClassName();
                    Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName(imageParam.getFormatName());
                    while (iter.hasNext()) {
                        ImageWriter writer = iter.next();
                        if (cl == null || writer.getClass().getName().equals(cl)) {
                            return new ImageWriterItem(writer, imageParam);
                        }
                    }
                }
            }
        }
        return null;
    }

    public static ImageWriter getImageWriter(ImageWriterParam param) {

        // ImageWriterSpi are laoded through the java ServiceLoader,
        // istead of imageio ServiceRegistry
        Iterator<ImageWriterSpi> iter = ServiceLoader.load(ImageWriterSpi.class).iterator();

        try {

            if (iter != null && iter.hasNext()) {

                do {
                    ImageWriterSpi writerspi = iter.next();
                    if (supportsFormat(writerspi.getFormatNames(), param.formatName)) {

                        ImageWriter writer = writerspi.createWriterInstance();

                        if (param.className == null || param.className.equals(writer.getClass().getName()))
                            return writer;
                    }
                } while (iter.hasNext());
            }

            throw new RuntimeException("No Image Writer for format: " + param.formatName + " registered");

        } catch (IOException e) {
            throw new RuntimeException("Error instantiating Writer for format: " + param.formatName);
        }
    }

    private static boolean supportsFormat(String[] supportedFormats, String format) {
        boolean supported = false;

        if (format != null && supportedFormats != null) {

            for (int i = 0; i < supportedFormats.length; i++)
                if (supportedFormats[i] != null && supportedFormats[i].trim().equalsIgnoreCase(format.trim()))
                    supported = true;
        }

        return supported;
    }
}
