/*
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.ade.contenteditor;

import com.alkacon.acacia.shared.AttributeConfiguration;
import com.alkacon.acacia.shared.ContentDefinition;
import com.alkacon.acacia.shared.Entity;
import com.alkacon.acacia.shared.Type;
import com.alkacon.vie.shared.I_Entity;
import com.alkacon.vie.shared.I_EntityAttribute;
import com.alkacon.vie.shared.I_Type;

import org.opencms.ade.contenteditor.shared.CmsContentDefinition;
import org.opencms.ade.contenteditor.shared.CmsExternalWidgetConfiguration;
import org.opencms.ade.contenteditor.shared.rpc.I_CmsContentService;
import org.opencms.file.CmsFile;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.types.CmsResourceTypeXmlContent;
import org.opencms.flex.CmsFlexController;
import org.opencms.gwt.CmsGwtService;
import org.opencms.gwt.CmsRpcException;
import org.opencms.i18n.CmsEncoder;
import org.opencms.i18n.CmsLocaleManager;
import org.opencms.i18n.CmsMessages;
import org.opencms.i18n.CmsMultiMessages;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.util.CmsStringUtil;
import org.opencms.util.CmsUUID;
import org.opencms.widgets.A_CmsWidget;
import org.opencms.widgets.I_CmsADEWidget;
import org.opencms.widgets.I_CmsWidget;
import org.opencms.workplace.CmsDialog;
import org.opencms.workplace.editors.CmsEditor;
import org.opencms.workplace.editors.Messages;
import org.opencms.xml.CmsXmlContentDefinition;
import org.opencms.xml.content.CmsXmlContent;
import org.opencms.xml.content.CmsXmlContentFactory;
import org.opencms.xml.content.I_CmsXmlContentHandler;
import org.opencms.xml.types.CmsXmlNestedContentDefinition;
import org.opencms.xml.types.I_CmsXmlContentValue;
import org.opencms.xml.types.I_CmsXmlSchemaType;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;

import org.dom4j.Element;

/**
 * Service to provide entity persistence within OpenCms.<p>
 */
public class CmsContentService extends CmsGwtService implements I_CmsContentService {

    /**
     * Visitor to read all types and attribute configurations within a content definition.<p>
     */
    protected class TypeVisitor {

        /** The attribute configurations. */
        private Map<String, AttributeConfiguration> m_attributeConfigurations;

        /** The content handler. */
        private I_CmsXmlContentHandler m_contentHandler;

        /** The content resource. */
        private CmsFile m_file;

        /** The content locale. */
        private Locale m_locale;

        /** The messages. */
        private CmsMultiMessages m_messages;

        /** The registered types. */
        private Map<String, I_Type> m_registeredTypes;

        /** The widget configurations. */
        private Map<String, CmsExternalWidgetConfiguration> m_widgetConfigurations;

        /**
         * Constructor.<p>
         * 
         * @param file the content file
         * @param locale the content locale
         */
        protected TypeVisitor(CmsFile file, Locale locale) {

            m_file = file;
            m_locale = locale;
        }

        /**
         * Returns the attribute configurations.<p>
         * 
         * @return the attribute configurations
         */
        protected Map<String, AttributeConfiguration> getAttributeConfigurations() {

            return m_attributeConfigurations;
        }

        /**
         * Returns the types of the visited content definition.<p>
         * 
         * @return the types
         */
        protected Map<String, I_Type> getTypes() {

            return m_registeredTypes;
        }

        /**
         * Returns the external widget configurations.<p>
         * 
         * @return the external widget configurations
         */
        protected Collection<CmsExternalWidgetConfiguration> getWidgetConfigurations() {

            return m_widgetConfigurations.values();
        }

        /**
         * Visits all types within the XML content definition.<p>
         * 
         * @param xmlContentDefinition the content definition
         * @param locale the locale
         */
        protected void visitTypes(CmsXmlContentDefinition xmlContentDefinition, Locale locale) {

            m_contentHandler = xmlContentDefinition.getContentHandler();
            CmsMessages messages = null;
            m_messages = new CmsMultiMessages(locale);
            try {
                messages = OpenCms.getWorkplaceManager().getMessages(locale);
                m_messages.addMessages(messages);
                m_messages.addMessages(m_contentHandler.getMessages(locale));
            } catch (Exception e) {
                //ignore
            }
            // generate a new multi messages object and add the messages from the workplace

            m_attributeConfigurations = new HashMap<String, AttributeConfiguration>();
            m_widgetConfigurations = new HashMap<String, CmsExternalWidgetConfiguration>();
            m_registeredTypes = new HashMap<String, I_Type>();
            readTypes(xmlContentDefinition, "");
        }

        /**
         * Returns the help information for this value.<p>
         * 
         * @param value the value
         * 
         * @return the help information
         */
        private String getHelp(I_CmsXmlSchemaType value) {

            StringBuffer result = new StringBuffer(64);
            result.append(A_CmsWidget.LABEL_PREFIX);
            result.append(getTypeKey(value));
            result.append(A_CmsWidget.HELP_POSTFIX);
            return m_messages.keyDefault(result.toString(), value.getName());
        }

        /**
         * Returns the label for this value.<p>
         * 
         * @param value the value
         * 
         * @return the label
         */
        private String getLabel(I_CmsXmlSchemaType value) {

            StringBuffer result = new StringBuffer(64);
            result.append(A_CmsWidget.LABEL_PREFIX);
            result.append(getTypeKey(value));
            return m_messages.keyDefault(result.toString(), value.getName());
        }

        /**
         * Returns the schema type message key.<p>
         * 
         * @param value the schema type
         * 
         * @return the schema type message key
         */
        private String getTypeKey(I_CmsXmlSchemaType value) {

            StringBuffer result = new StringBuffer(64);
            result.append(value.getContentDefinition().getInnerName());
            result.append('.');
            result.append(value.getName());
            return result.toString();
        }

        /**
         * Reads the attribute configuration for the given schema type. May return <code>null</code> if no special configuration was set.<p>
         * 
         * @param schemaType the schema type
         * @param path the attribute path
         * 
         * @return the attribute configuration
         */
        private AttributeConfiguration readConfiguration(I_CmsXmlSchemaType schemaType, String path) {

            AttributeConfiguration result = null;
            String widgetName = null;
            String widgetConfig = null;
            try {
                I_CmsWidget widget = m_contentHandler.getWidget(schemaType);
                widgetName = widget.getClass().getName();
                widgetConfig = widget.getConfiguration();
                if (widget instanceof I_CmsADEWidget) {
                    I_CmsADEWidget adeWidget = (I_CmsADEWidget)widget;
                    if (!adeWidget.isInternal() && !m_widgetConfigurations.containsKey(widgetName)) {
                        CmsExternalWidgetConfiguration externalConfiguration = new CmsExternalWidgetConfiguration(
                            widgetName,
                            adeWidget.getInitCall(),
                            adeWidget.getJavaScriptResourceLinks(getCmsObject()),
                            adeWidget.getCssResourceLinks(getCmsObject()));
                        m_widgetConfigurations.put(widgetName, externalConfiguration);
                    }
                }
            } catch (Exception e) {
                // may happen if no widget was set for the value
                LOG.debug(e.getMessage(), e);
            }
            result = new AttributeConfiguration(
                getLabel(schemaType),
                getHelp(schemaType),
                widgetName,
                widgetConfig,
                readDefaultValue(schemaType, path));
            return result;
        }

        /**
         * Reads the default value for the given type.<p>
         * 
         * @param schemaType the schema type
         * @param path the element path
         * 
         * @return the default value
         */
        private String readDefaultValue(I_CmsXmlSchemaType schemaType, String path) {

            return m_contentHandler.getDefault(getCmsObject(), m_file, schemaType, path, m_locale);
        }

        /**
         * Reads the types from the given content definition and adds the to the map of already registered
         * types if necessary.<p>
         * 
         * @param xmlContentDefinition the XML content definition
         * @param path the element path
         * @param attributeName the attribute name
         */
        private void readTypes(CmsXmlContentDefinition xmlContentDefinition, String path) {

            String typeName = getTypeUri(xmlContentDefinition);
            if (m_registeredTypes.containsKey(typeName)) {
                return;
            }
            Type type = new Type(typeName);
            m_registeredTypes.put(typeName, type);

            for (I_CmsXmlSchemaType subType : xmlContentDefinition.getTypeSequence()) {

                String subTypeName = null;
                String childPath = path + "/" + subType.getName();
                String subAttributeName = getAttributeName(subType.getName(), typeName);
                AttributeConfiguration config = readConfiguration(subType, childPath);
                if (config != null) {
                    m_attributeConfigurations.put(subAttributeName, config);
                }
                if (subType.isSimpleType()) {
                    subTypeName = TYPE_NAME_PREFIX + subType.getTypeName();
                    if (!m_registeredTypes.containsKey(subTypeName)) {
                        m_registeredTypes.put(subTypeName, new Type(subTypeName));
                    }
                } else {
                    CmsXmlContentDefinition subTypeDefinition = ((CmsXmlNestedContentDefinition)subType).getNestedContentDefinition();
                    subTypeName = getTypeUri(subTypeDefinition);
                    readTypes(subTypeDefinition, childPath);
                }
                type.addAttribute(subAttributeName, subTypeName, subType.getMinOccurs(), subType.getMaxOccurs());
            }
        }
    }

    /** The logger for this class. */
    protected static final Log LOG = CmsLog.getLog(CmsContentService.class);

    /** The serial version id. */
    private static final long serialVersionUID = 7873052619331296648L;

    /** The type name prefix. */
    private static final String TYPE_NAME_PREFIX = "http://opencms.org/types/";

    /**
     * Returns a new configured service instance.<p>
     * 
     * @param request the current request
     * 
     * @return a new service instance
     */
    public static CmsContentService newInstance(HttpServletRequest request) {

        CmsContentService srv = new CmsContentService();
        srv.setCms(CmsFlexController.getCmsObject(request));
        srv.setRequest(request);
        return srv;
    }

    /**
     * @see com.alkacon.acacia.shared.rpc.I_ContentService#loadContentDefinition(java.lang.String)
     */
    public ContentDefinition loadContentDefinition(String entityId) throws CmsRpcException {

        return loadDefinition(entityId);
    }

    /**
     * @see org.opencms.ade.contenteditor.shared.rpc.I_CmsContentService#loadDefinition(java.lang.String)
     */
    public CmsContentDefinition loadDefinition(String entityId) throws CmsRpcException {

        CmsContentDefinition definition = null;
        try {
            CmsUUID structureId = CmsContentDefinition.entityIdToUuid(entityId);
            CmsResource resource = getCmsObject().readResource(structureId);
            Locale contentLocale = CmsLocaleManager.getLocale(CmsContentDefinition.getLocaleFromId(entityId));
            definition = readContentDefinition(
                resource,
                CmsContentDefinition.uuidToEntityId(structureId, contentLocale.toString()),
                contentLocale,
                false);
        } catch (Exception e) {
            error(e);
        }
        return definition;
    }

    /**
     * @see org.opencms.ade.contenteditor.shared.rpc.I_CmsContentService#loadNewDefinition(java.lang.String)
     */
    public CmsContentDefinition loadNewDefinition(String entityId) throws CmsRpcException {

        CmsContentDefinition definition = null;
        try {
            CmsUUID structureId = CmsContentDefinition.entityIdToUuid(entityId);
            CmsResource resource = getCmsObject().readResource(structureId);
            Locale contentLocale = CmsLocaleManager.getLocale(CmsContentDefinition.getLocaleFromId(entityId));
            definition = readContentDefinition(
                resource,
                CmsContentDefinition.uuidToEntityId(structureId, contentLocale.toString()),
                contentLocale,
                true);
        } catch (Exception e) {
            error(e);
        }
        return definition;
    }

    /**
     * @see org.opencms.ade.contenteditor.shared.rpc.I_CmsContentService#prefetch()
     */
    public CmsContentDefinition prefetch() throws CmsRpcException {

        String paramResource = getRequest().getParameter(CmsDialog.PARAM_RESOURCE);
        if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(paramResource)) {
            try {
                CmsResource resource = getCmsObject().readResource(paramResource);
                if (CmsResourceTypeXmlContent.isXmlContent(resource)) {
                    String paramLocale = getRequest().getParameter(CmsEditor.PARAM_ELEMENTLANGUAGE);
                    Locale locale;
                    if (CmsStringUtil.isNotEmptyOrWhitespaceOnly(paramLocale)) {
                        locale = CmsLocaleManager.getLocale(paramLocale);
                    } else {
                        locale = OpenCms.getLocaleManager().getDefaultLocale(getCmsObject(), resource);
                    }

                    return readContentDefinition(resource, null, locale, false);
                }
            } catch (Exception e) {
                error(e);
            }

        }
        return null;
    }

    /**
     * @see org.opencms.ade.contenteditor.shared.rpc.I_CmsContentService#saveAndDeleteEntites(java.util.List, java.util.List)
     */
    public void saveAndDeleteEntites(List<Entity> changedEntities, List<String> deletedEntities) throws CmsRpcException {

        CmsUUID structureId = null;
        if (!changedEntities.isEmpty()) {
            structureId = CmsContentDefinition.entityIdToUuid(changedEntities.get(0).getId());
        }
        if ((structureId == null) && !deletedEntities.isEmpty()) {
            structureId = CmsContentDefinition.entityIdToUuid(deletedEntities.get(0));
        }
        if (structureId != null) {
            CmsObject cms = getCmsObject();
            try {
                CmsResource resource = cms.readResource(structureId);
                ensureLock(resource);
                CmsFile file = cms.readFile(resource);
                CmsXmlContent content = CmsXmlContentFactory.unmarshal(cms, file);
                for (Entity entity : changedEntities) {
                    String entityId = entity.getId();
                    Locale contentLocale = CmsLocaleManager.getLocale(CmsContentDefinition.getLocaleFromId(entityId));
                    if (content.hasLocale(contentLocale)) {
                        content.removeLocale(contentLocale);
                    }
                    content.addLocale(cms, contentLocale);
                    addEntityAttributes(cms, content, "", entity, contentLocale);
                }
                for (String deleteId : deletedEntities) {
                    Locale contentLocale = CmsLocaleManager.getLocale(CmsContentDefinition.getLocaleFromId(deleteId));
                    if (content.hasLocale(contentLocale)) {
                        content.removeLocale(contentLocale);
                    }
                }
                writeContent(cms, file, content, getFileEncoding(cms, file));
                tryUnlock(resource);
            } catch (Exception e) {
                error(e);
            }
        }
    }

    /**
     * @see com.alkacon.acacia.shared.rpc.I_ContentService#saveEntities(java.util.List)
     */
    public void saveEntities(List<Entity> entities) throws CmsRpcException {

        saveAndDeleteEntites(entities, Collections.<String> emptyList());
    }

    /**
     * @see com.alkacon.acacia.shared.rpc.I_ContentService#saveEntity(com.alkacon.acacia.shared.Entity)
     */
    public void saveEntity(Entity entity) throws CmsRpcException {

        saveEntities(Collections.singletonList(entity));
    }

    /**
     * Returns the entity attribute name to use for this element.<p>
     * 
     * @param elementName the element name
     * @param parentType the parent type
     * 
     * @return the attribute name
     */
    protected String getAttributeName(String elementName, String parentType) {

        return parentType + "/" + elementName;
    }

    /**
     * Returns the element name to the given element.<p>
     * 
     * @param attributeName the attribute name
     * 
     * @return the element name
     */
    protected String getElementName(String attributeName) {

        if (attributeName.contains("/")) {
            return attributeName.substring(attributeName.lastIndexOf("/") + 1);
        }
        return attributeName;
    }

    /**
     * Helper method to determine the encoding of the given file in the VFS,
     * which must be set using the "content-encoding" property.<p>
     * 
     * @param cms the CmsObject
     * @param file the file which is to be checked
     * @return the encoding for the file
     */
    protected String getFileEncoding(CmsObject cms, CmsResource file) {

        String result;
        try {
            result = cms.readPropertyObject(file, CmsPropertyDefinition.PROPERTY_CONTENT_ENCODING, true).getValue(
                OpenCms.getSystemInfo().getDefaultEncoding());
        } catch (CmsException e) {
            result = OpenCms.getSystemInfo().getDefaultEncoding();
        }
        return CmsEncoder.lookupEncoding(result, OpenCms.getSystemInfo().getDefaultEncoding());
    }

    /**
     * Returns the type URI.<p>
     * 
     * @param xmlContentDefinition the type content definition
     * 
     * @return the type URI
     */
    protected String getTypeUri(CmsXmlContentDefinition xmlContentDefinition) {

        return xmlContentDefinition.getSchemaLocation() + "/" + xmlContentDefinition.getTypeName();
    }

    /**
     * Parses the element into an entity.<p>
     * 
     * @param content the entity content
     * @param element the current element
     * @param locale the content locale
     * @param entityId the entity id
     * @param parentPath the parent path
     * @param typeName the entity type name
     * @param registeredTypes the types used within the entity
     * 
     * @return the entity
     */
    protected Entity readEntity(
        CmsXmlContent content,
        Element element,
        Locale locale,
        String entityId,
        String parentPath,
        String typeName,
        Map<String, I_Type> registeredTypes) {

        Entity result = new Entity(entityId + parentPath, typeName);
        @SuppressWarnings("unchecked")
        List<Element> elements = element.elements();
        I_Type type = registeredTypes.get(typeName);
        int counter = 1;
        CmsObject cms = getCmsObject();
        String previousName = null;
        for (Element child : elements) {
            String attributeName = getAttributeName(child.getName(), typeName);
            if (!attributeName.equals(previousName)) {
                // reset the attribute counter for every attribute name
                counter = 1;
                previousName = attributeName;
            }
            String subTypeName = type.getAttributeTypeName(attributeName);
            String path = parentPath + "/" + child.getName() + "[" + counter + "]";
            if (registeredTypes.get(subTypeName).isSimpleType()) {
                I_CmsXmlContentValue value = content.getValue(path, locale);
                result.addAttributeValue(attributeName, value.getStringValue(cms));
            } else {
                Entity subEntity = readEntity(content, child, locale, entityId, path, subTypeName, registeredTypes);
                result.addAttributeValue(attributeName, subEntity);

            }
            counter++;
        }
        return result;
    }

    /**
     * Reads the types from the given content definition and adds the to the map of already registered
     * types if necessary.<p>
     * 
     * @param xmlContentDefinition the XML content definition
     * @param locale the messages locale
     * 
     * @return the types of the given content definition 
     */
    protected Map<String, I_Type> readTypes(CmsXmlContentDefinition xmlContentDefinition, Locale locale) {

        TypeVisitor visitor = new TypeVisitor(null, locale);
        visitor.visitTypes(xmlContentDefinition, locale);
        return visitor.getTypes();
    }

    /**
     * Adds the attribute values of the entity to the given XML content.<p>
     * 
     * @param cms the current cms context
     * @param content the XML content
     * @param parentPath the parent path
     * @param entity the entity
     * @param contentLocale the content locale
     */
    private void addEntityAttributes(
        CmsObject cms,
        CmsXmlContent content,
        String parentPath,
        I_Entity entity,
        Locale contentLocale) {

        for (I_EntityAttribute attribute : entity.getAttributes()) {
            String elementPath = parentPath + getElementName(attribute.getAttributeName());
            if (attribute.isSimpleValue()) {
                List<String> values = attribute.getSimpleValues();
                for (int i = 0; i < values.size(); i++) {
                    String value = values.get(i);
                    I_CmsXmlContentValue field = content.getValue(elementPath, contentLocale, i);
                    if (field == null) {
                        field = content.addValue(cms, elementPath, contentLocale, i);
                    }
                    field.setStringValue(cms, value);

                }
            } else {
                List<I_Entity> entities = attribute.getComplexValues();
                for (int i = 0; i < entities.size(); i++) {
                    I_Entity child = entities.get(i);
                    I_CmsXmlContentValue field = content.getValue(elementPath, contentLocale, i);
                    if (field == null) {
                        field = content.addValue(cms, elementPath, contentLocale, i);
                    }
                    addEntityAttributes(cms, content, field.getPath() + "/", child, contentLocale);
                }
            }
        }
    }

    /**
     * Reads the content definition for the given resource and locale.<p>
     * 
     * @param resource the resource
     * @param entityId the entity id
     * @param locale the content locale
     * @param newLocale if the locale content should be created as new
     * 
     * @return the content definition
     * 
     * @throws CmsException if something goes wrong
     */
    private CmsContentDefinition readContentDefinition(
        CmsResource resource,
        String entityId,
        Locale locale,
        boolean newLocale) throws CmsException {

        CmsObject cms = getCmsObject();
        if (CmsStringUtil.isEmptyOrWhitespaceOnly(entityId)) {
            entityId = CmsContentDefinition.uuidToEntityId(resource.getStructureId(), locale.toString());
        }
        CmsFile file = cms.readFile(resource);
        CmsXmlContent content = CmsXmlContentFactory.unmarshal(cms, file);

        TypeVisitor visitor = new TypeVisitor(file, locale);
        visitor.visitTypes(content.getContentDefinition(), locale);
        Entity entity = null;
        if (content.hasLocale(locale) && newLocale) {
            // a new locale is requested, so remove the present one
            content.removeLocale(locale);
        }
        if (!content.hasLocale(locale)) {
            content.addLocale(cms, locale);
        }
        Element element = content.getLocaleNode(locale);
        entity = readEntity(
            content,
            element,
            locale,
            entityId,
            "",
            getTypeUri(content.getContentDefinition()),
            visitor.getTypes());

        List<String> contentLocales = new ArrayList<String>();
        for (Locale contentLocale : content.getLocales()) {
            contentLocales.add(contentLocale.toString());
        }
        Locale workplaceLocale = OpenCms.getWorkplaceManager().getWorkplaceLocale(cms);
        TreeMap<String, String> availableLocales = new TreeMap<String, String>();
        for (Locale availableLocale : OpenCms.getLocaleManager().getAvailableLocales()) {
            availableLocales.put(availableLocale.toString(), availableLocale.getDisplayName(workplaceLocale));
        }
        String title = cms.readPropertyObject(resource, CmsPropertyDefinition.PROPERTY_TITLE, false).getValue();
        String typeName = OpenCms.getResourceManager().getResourceType(resource.getTypeId()).getTypeName();
        return new CmsContentDefinition(
            entity,
            visitor.getAttributeConfigurations(),
            visitor.getWidgetConfigurations(),
            visitor.getTypes(),
            locale.toString(),
            contentLocales,
            availableLocales,
            title,
            cms.getSitePath(resource),
            typeName);
    }

    /**
     * Writes the xml content to the vfs and re-initializes the member variables.<p>
     * 
     * @param cms the cms context
     * @param file the file to write to
     * @param content the content
     * @param encoding the file encoding
     * 
     * @return the content 
     * 
     * @throws CmsException if writing the file fails
     */
    private CmsXmlContent writeContent(CmsObject cms, CmsFile file, CmsXmlContent content, String encoding)
    throws CmsException {

        String decodedContent = content.toString();
        try {
            file.setContents(decodedContent.getBytes(encoding));
        } catch (UnsupportedEncodingException e) {
            throw new CmsException(Messages.get().container(Messages.ERR_INVALID_CONTENT_ENC_1, file.getRootPath()), e);
        }
        // the file content might have been modified during the write operation    
        file = cms.writeFile(file);
        return CmsXmlContentFactory.unmarshal(cms, file);
    }
}