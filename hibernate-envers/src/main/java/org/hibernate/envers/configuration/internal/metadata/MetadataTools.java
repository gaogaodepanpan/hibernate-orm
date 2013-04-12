/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.envers.configuration.internal.metadata;
import java.util.Iterator;
import javax.persistence.JoinColumn;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.Element;

import org.hibernate.envers.internal.tools.StringTools;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Formula;
import org.hibernate.mapping.Selectable;

/**
 * @author Adam Warski (adam at warski dot org)
 * @author Lukasz Antoniak (lukasz dot antoniak at gmail dot com)
 * @author Michal Skowronek (mskowr at o2 dot pl)
 */
public class MetadataTools {

	public static Element addNativelyGeneratedId(Element parent, String name, String type,
                                                 boolean useRevisionEntityWithNativeId) {
        Element id_mapping = parent.addElement("id");
        id_mapping.addAttribute("name", name).addAttribute("type", type);

        Element generator_mapping = id_mapping.addElement("generator");
        if (useRevisionEntityWithNativeId) {
            generator_mapping.addAttribute("class", "native");
        } else {
            generator_mapping.addAttribute("class", "org.hibernate.envers.enhanced.OrderedSequenceGenerator");
            generator_mapping.addElement("param").addAttribute("name", "sequence_name").setText("REVISION_GENERATOR");
            generator_mapping.addElement("param").addAttribute("name", "table_name").setText("REVISION_GENERATOR");
            generator_mapping.addElement("param").addAttribute("name", "initial_value").setText("1");
            generator_mapping.addElement("param").addAttribute("name", "increment_size").setText("1");
        }
//        generator_mapping.addAttribute("class", "sequence");
//        generator_mapping.addElement("param").addAttribute("name", "sequence").setText("custom");

        return id_mapping;
    }

    public static Element addProperty(Element parent, String name, String type, boolean insertable, boolean updateable, boolean key) {
        Element prop_mapping;
        if (key) {
            prop_mapping = parent.addElement("key-property");
        } else {
            prop_mapping = parent.addElement("property");
        }

        prop_mapping.addAttribute("name", name);
        prop_mapping.addAttribute("insert", Boolean.toString(insertable));
        prop_mapping.addAttribute("update", Boolean.toString(updateable));

        if (type != null) {
            prop_mapping.addAttribute("type", type);
        }

        return prop_mapping;
    }

    public static Element addProperty(Element parent, String name, String type, boolean insertable, boolean key) {
        return addProperty(parent, name, type, insertable, false, key);
    }

	public static Element addModifiedFlagProperty(Element parent, String propertyName, String suffix) {
		return addProperty(parent, getModifiedFlagPropertyName(propertyName, suffix), "boolean", true, false, false);
	}

	public static String getModifiedFlagPropertyName(String propertyName, String suffix) {
		return propertyName + suffix;
	}

	private static void addOrModifyAttribute(Element parent, String name, String value) {
        Attribute attribute = parent.attribute(name);
        if (attribute == null) {
            parent.addAttribute(name, value);
        } else {
            attribute.setValue(value);
        }
    }

    /**
     * Column name shall be wrapped with '`' signs if quotation required.
     */
    public static Element addOrModifyColumn(Element parent, String name) {
        Element column_mapping = parent.element("column");

        if (column_mapping == null) {
            return addColumn(parent, name, null, null, null, null, null, null);
        }

        if (!StringTools.isEmpty(name)) {
            addOrModifyAttribute(column_mapping, "name", name);
        }

        return column_mapping;
    }

    /**
     * Adds new <code>column</code> element. Method assumes that the value of <code>name</code> attribute is already
     * wrapped with '`' signs if quotation required. It shall be invoked when column name is taken directly from configuration
     * file and not from {@link org.hibernate.mapping.PersistentClass} descriptor.
     */
    public static Element addColumn(Element parent, String name, Integer length, Integer scale, Integer precision,
									String sqlType, String customRead, String customWrite) {
        return addColumn(parent, name, length, scale, precision, sqlType, customRead, customWrite, false);
    }

    public static Element addColumn(Element parent, String name, Integer length, Integer scale, Integer precision,
									String sqlType, String customRead, String customWrite, boolean quoted) {
        Element column_mapping = parent.addElement("column");

        column_mapping.addAttribute("name", quoted ? "`" + name + "`" : name);
        if (length != null) {
            column_mapping.addAttribute("length", length.toString());
        }
		if (scale != null) {
			column_mapping.addAttribute("scale", Integer.toString(scale));
		}
		if (precision != null) {
			column_mapping.addAttribute("precision", Integer.toString(precision));
		}
		if (!StringTools.isEmpty(sqlType)) {
            column_mapping.addAttribute("sql-type", sqlType);
        }

        if (!StringTools.isEmpty(customRead)) {
            column_mapping.addAttribute("read", customRead);
        }
        if (!StringTools.isEmpty(customWrite)) {
            column_mapping.addAttribute("write", customWrite);
        }

        return column_mapping;
    }

    private static Element createEntityCommon(Document document, String type, AuditTableData auditTableData,
                                              String discriminatorValue, Boolean isAbstract) {
        Element hibernate_mapping = document.addElement("hibernate-mapping");
        hibernate_mapping.addAttribute("auto-import", "false");

        Element class_mapping = hibernate_mapping.addElement(type);

        if (auditTableData.getAuditEntityName() != null) {
            class_mapping.addAttribute("entity-name", auditTableData.getAuditEntityName());
        }

        if (discriminatorValue != null) {
            class_mapping.addAttribute("discriminator-value", discriminatorValue);
        }

        if (!StringTools.isEmpty(auditTableData.getAuditTableName())) {
            class_mapping.addAttribute("table", auditTableData.getAuditTableName());
        }

        if (!StringTools.isEmpty(auditTableData.getSchema())) {
            class_mapping.addAttribute("schema", auditTableData.getSchema());
        }

        if (!StringTools.isEmpty(auditTableData.getCatalog())) {
            class_mapping.addAttribute("catalog", auditTableData.getCatalog());
        }

        if (isAbstract != null) {
            class_mapping.addAttribute("abstract", isAbstract.toString());
        }

        return class_mapping;
    }

    public static Element createEntity(Document document, AuditTableData auditTableData, String discriminatorValue,
                                       Boolean isAbstract) {
        return createEntityCommon(document, "class", auditTableData, discriminatorValue, isAbstract);
    }

    public static Element createSubclassEntity(Document document, String subclassType, AuditTableData auditTableData,
                                               String extendsEntityName, String discriminatorValue, Boolean isAbstract) {
        Element class_mapping = createEntityCommon(document, subclassType, auditTableData, discriminatorValue, isAbstract);

        class_mapping.addAttribute("extends", extendsEntityName);

        return class_mapping;
    }

    public static Element createJoin(Element parent, String tableName,
                                     String schema, String catalog) {
        Element join_mapping = parent.addElement("join");

        join_mapping.addAttribute("table", tableName);

        if (!StringTools.isEmpty(schema)) {
            join_mapping.addAttribute("schema", schema);
        }

        if (!StringTools.isEmpty(catalog)) {
            join_mapping.addAttribute("catalog", catalog);
        }

        return join_mapping;
    }

    public static void addColumns(Element any_mapping, Iterator selectables) {
        while ( selectables.hasNext() ) {
			final Selectable selectable = (Selectable) selectables.next();
			if ( selectable.isFormula() ) {
				throw new FormulaNotSupportedException();
			}
            addColumn( any_mapping, (Column) selectable );
        }
    }

    /**
     * Adds <code>column</code> element with the following attributes (unless empty): <code>name</code>,
     * <code>length</code>, <code>scale</code>, <code>precision</code>, <code>sql-type</code>, <code>read</code>
     * and <code>write</code>.
     * @param any_mapping Parent element.
     * @param column Column descriptor.
     */
    public static void addColumn(Element any_mapping, Column column) {
        addColumn(any_mapping, column.getName(), column.getLength(), column.getScale(), column.getPrecision(),
                  column.getSqlType(), column.getCustomRead(), column.getCustomWrite(), column.isQuoted());
    }

    @SuppressWarnings({"unchecked"})
    private static void changeNamesInColumnElement(Element element, ColumnNameIterator columnNameIterator) {
        Iterator<Element> properties = element.elementIterator();
        while (properties.hasNext()) {
            Element property = properties.next();

            if ("column".equals(property.getName())) {
                Attribute nameAttr = property.attribute("name");
                if (nameAttr != null) {
                    nameAttr.setText(columnNameIterator.next());
                }
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    public static void prefixNamesInPropertyElement(Element element, String prefix, ColumnNameIterator columnNameIterator,
                                                    boolean changeToKey, boolean insertable) {
        Iterator<Element> properties = element.elementIterator();
        while (properties.hasNext()) {
            Element property = properties.next();

            if ("property".equals(property.getName()) || "many-to-one".equals(property.getName())) {
                Attribute nameAttr = property.attribute("name");
                if (nameAttr != null) {
                    nameAttr.setText(prefix + nameAttr.getText());
                }

                changeNamesInColumnElement(property, columnNameIterator);

                if (changeToKey) {
                    property.setName("key-" + property.getName());
                }

                if ("property".equals(property.getName())) {
                    Attribute insert = property.attribute("insert");
                    insert.setText(Boolean.toString(insertable));
                }
            }
        }
    }

    /**
     * Adds <code>formula</code> element.
     * @param element Parent element.
     * @param formula Formula descriptor.
     */
    public static void addFormula(Element element, Formula formula) {
        element.addElement("formula").setText(formula.getText());
    }

    /**
     * Adds all <code>column</code> or <code>formula</code> elements.
     * @param element Parent element.
     * @param columnIterator Iterator pointing at {@link org.hibernate.mapping.Column} and/or
     *                       {@link org.hibernate.mapping.Formula} objects.
     */
    public static void addColumnsOrFormulas(Element element, Iterator columnIterator) {
        while (columnIterator.hasNext()) {
            Object o = columnIterator.next();
            if (o instanceof Column) {
                addColumn(element, (Column) o);
            } else if (o instanceof Formula) {
                addFormula(element, (Formula) o);
            }
        }
    }

    /**
     * An iterator over column names.
     */
    public static abstract class ColumnNameIterator implements Iterator<String> {
	}

    public static ColumnNameIterator getColumnNameIterator(final Iterator<Selectable> selectableIterator) {
        return new ColumnNameIterator() {
            public boolean hasNext() {
				return selectableIterator.hasNext();
			}

            public String next() {
				final Selectable next = selectableIterator.next();
				if ( next.isFormula() ) {
					throw new FormulaNotSupportedException();
				}
				return ( (Column) next ).getName();
			}

            public void remove() {
				selectableIterator.remove();
			}
        };
    }

    public static ColumnNameIterator getColumnNameIterator(final JoinColumn[] joinColumns) {
        return new ColumnNameIterator() {
            int counter = 0;
            public boolean hasNext() { return counter < joinColumns.length; }
            public String next() { return joinColumns[counter++].name(); }
            public void remove() { throw new UnsupportedOperationException(); }
        };
    }
}
