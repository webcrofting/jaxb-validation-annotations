// NOPMD
package com.crofting.jaxbvalidation;

import com.sun.codemodel.JAnnotationUse;
import com.sun.xml.xsom.XSComponent;


import org.xml.sax.ErrorHandler;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.tools.xjc.Options;
import com.sun.tools.xjc.Plugin;
import com.sun.tools.xjc.outline.ClassOutline;
import com.sun.tools.xjc.outline.FieldOutline;
import com.sun.tools.xjc.outline.Outline;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSFacet;
import java.math.BigDecimal;
import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Pattern;
import org.hibernate.validator.constraints.Length;

public class ValidationPlugin extends Plugin {

    @Override
    public String getOptionName() {
        return "Xvalidate";
    }

    @Override
    public String getUsage() {
        return "TBD";
    }
    private String defaultFieldTarget = "getter";

    public String getDefaultFieldTarget() {
        return defaultFieldTarget;
    }

    public void setDefaultFieldTarget(String defaultFieldTarget) {
        if ("getter".equals(defaultFieldTarget)
                || "setter".equals(defaultFieldTarget)
                || "setter-parameter".equals(defaultFieldTarget)
                || "field".equals(defaultFieldTarget)) {
            this.defaultFieldTarget = defaultFieldTarget;
        } else {
            throw new IllegalArgumentException("Invalid default field target.");
        }
    }

    @Override
    public boolean run(Outline outline, Options options,
            ErrorHandler errorHandler) {


        for (final ClassOutline classOutline : outline.getClasses()) {
            processClassOutline(classOutline, options, errorHandler);
        }
        return true;
    }

    protected void processClassOutline(ClassOutline classOutline,
            Options options, ErrorHandler errorHandler) {



        for (final FieldOutline fieldOutline : classOutline.getDeclaredFields()) {
            processFieldOutline(classOutline, fieldOutline, options,
                    errorHandler);
        }

    }

    private Number parseAndNormalizeMaxMinValue(final FieldOutline fieldOutline, final JCodeModel codeModel, XSFacet facet) throws NumberFormatException {
        try {

            long parsedMax = Long.parseLong(facet.getValue().toString());
            final Long normalizedParsedValue = parsedMax > Long.MAX_VALUE ? Long.MAX_VALUE : (long) parsedMax;
            return normalizedParsedValue;
        } catch (NumberFormatException e) {
            double parsedMax = Double.parseDouble(facet.getValue().toString());
            final Double normalizedParsedValue = parsedMax > Double.MAX_VALUE ? Double.MAX_VALUE : (double) parsedMax;
            return normalizedParsedValue;
        }
    }

    private void processFieldOutline(ClassOutline classOutline,
            FieldOutline fieldOutline, Options options,
            ErrorHandler errorHandler) {
        annotate(fieldOutline.parent().ref.owner(), fieldOutline,
                errorHandler);
    }

    protected void annotate(final JCodeModel codeModel,
            final FieldOutline fieldOutline,
            ErrorHandler errorHandler) {

        //fieldOutline.getPropertyInfo().getSchemaComponent().
        JFieldVar var = field(fieldOutline);



        if (var != null) {
            XSComponent schemaComponent = fieldOutline.getPropertyInfo().getSchemaComponent();
            if (schemaComponent instanceof XSAttributeUse) {
                XSAttributeUse attributeUse = (XSAttributeUse) schemaComponent;

                int maxlength = -1;
                int minlength = -1;

                if (attributeUse.getDecl().getType().isRestriction()) {

                    for (XSFacet facet : attributeUse.getDecl().getType().asRestriction().getDeclaredFacets()) {
                        // maxLength, pattern, minInclusive, minLength, enumeration, maxInclusive
                        if (XSFacet.FACET_PATTERN.equals(facet.getName()) && fieldOutline.getRawType().equals(codeModel.ref(String.class))) {
                            JAnnotationUse annotate = var.annotate(Pattern.class);
                            annotate.param("regexp", facet.getValue().value);
                        } else if (XSFacet.FACET_MAXLENGTH.equals(facet.getName())) {
                            maxlength = Integer.parseInt(facet.getValue().toString());
                        } else if (XSFacet.FACET_MINLENGTH.equals(facet.getName())) {
                            minlength = Integer.parseInt(facet.getValue().toString());
                        } else if (XSFacet.FACET_MAXINCLUSIVE.equals(facet.getName())) {
                            Number normalizedParsedValue = parseAndNormalizeMaxMinValue(fieldOutline, codeModel, facet);
                            if (normalizedParsedValue instanceof Double) {
                                JAnnotationUse annotate = var.annotate(DecimalMax.class);
                                
                                annotate.param("value", new BigDecimal(normalizedParsedValue.doubleValue()).toString());
                            } else {
                                JAnnotationUse annotate = var.annotate(Max.class);
                                annotate.param("value", normalizedParsedValue.longValue());
                            }

                            //System.out.println("************************ " + fieldOutline.);
                        } else if (XSFacet.FACET_MININCLUSIVE.equals(facet.getName())) {


                            Number normalizedParsedValue = parseAndNormalizeMaxMinValue(fieldOutline, codeModel, facet);
                            if (normalizedParsedValue instanceof Double) {
                                JAnnotationUse annotate = var.annotate(DecimalMin.class);
                                annotate.param("value", new BigDecimal(normalizedParsedValue.doubleValue()).toString());
                            } else {
                                JAnnotationUse annotate = var.annotate(Min.class);
                                annotate.param("value", normalizedParsedValue.longValue());
                            }
                        }
                    }
                    if (maxlength >= 0 || minlength >= 0) {
                        JAnnotationUse annotate = var.annotate(Length.class);
                        if (minlength > 0) {
                            annotate.param("min", minlength);
                        }
                        if (maxlength > 0) {
                            annotate.param("max", maxlength);
                        }
                    }
                }
            }
        }

    }

    public static JFieldVar field(FieldOutline fieldOutline) {
        final JDefinedClass theClass = fieldOutline.parent().implClass;
        return theClass.fields().get(
                fieldOutline.getPropertyInfo().getName(false));
    }
}
