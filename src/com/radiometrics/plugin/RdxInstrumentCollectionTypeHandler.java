/*
* Copyright (c) 2020 Radiometrics Inc.
*
*/

package com.radiometrics.plugin;


import org.ramadda.data.point.text.*;
import org.ramadda.data.point.text.CsvFile;
import org.ramadda.data.record.*;
import org.ramadda.data.services.PointTypeHandler;
import org.ramadda.repository.*;
import org.ramadda.repository.type.*;
import org.ramadda.util.HtmlUtils;
import org.ramadda.util.WikiUtil;

import org.w3c.dom.*;

import ucar.unidata.util.StringUtil;

import java.io.*;

import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.Hashtable;
import java.util.List;


/**
 *
 *
 */
public class RdxInstrumentCollectionTypeHandler extends ExtensibleGroupTypeHandler implements RdxConstants {

    /**
     * constructor
     *
     * @param repository The repository
     * @param entryNode xml node from rdxtypes.xml
     *
     * @throws Exception On badness
     */
    public RdxInstrumentCollectionTypeHandler(Repository repository,
            Element entryNode)
            throws Exception {
        super(repository, entryNode);
    }

    /**
     * This gets called on any wiki display tag for entries of this type.
     * This will add colors and colorBySteps properties if they haven't been specified
     * allowing for those properties to be globally set from the rdx.properties file
     *
     * @param entry the entry
     * @param wikiUtil util
     * @param tag wiki tag
     * @param props properties
     */
    @Override
    public void addWikiProperties(Entry entry, WikiUtil wikiUtil, String tag,
                                  Hashtable props) {
        if (props.get("colors") == null) {
            props.put("colors",
                      getRepository().getProperty("rdx.wiki.colors",
                          DEFAULT_COLORS));
        }
        if (props.get("colorBySteps") == null) {
            props.put("colorBySteps",
                      getRepository().getProperty("rdx.wiki.colorBySteps",
                          DEFAULT_COLORSTEPS));
        }

        if (props.get("colorTableLabels") == null) {
            props.put(
                "colorTableLabels",
                getRepository().getProperty(
                    "rdx.wiki.colorTableLabels", DEFAULT_COLORTABLELABELS));
        }




    }


}
