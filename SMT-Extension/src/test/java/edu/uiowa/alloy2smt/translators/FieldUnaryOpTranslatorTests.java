/*
 * This file is part of alloy2smt.
 * Copyright (C) 2020 Inria
 *
 * @author Philippe Merle
 *
 */

package edu.uiowa.alloy2smt.translators;

import edu.uiowa.alloy2smt.utils.CommandResult;
import edu.uiowa.alloy2smt.utils.AlloyUtils;

import java.util.List;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldUnaryOpTranslatorTests
{
    final static String alloySignatures =
        "sig A {}" +
        "sig LoneA { a : lone A }" + // Must be interpreted as all x : LoneA | lone x.a
        "sig OneA { a : one A }" // Must be interpreted as all x : OneA | one x.a
        ;
    
    @Test
    public void no_LoneA_with_several_A() throws Exception
    {
        String alloy = alloySignatures + "check { no x : LoneA | #(x.a) > 1 } expect 0";
        List<CommandResult> commandResults = AlloyUtils.runAlloyString(alloy, false);
        assertEquals("unsat", commandResults.get(0).satResult);
    }
    
    @Test
    public void no_OneA_without_A() throws Exception
    {
        String alloy = alloySignatures + "check { no x : OneA | no x.a } expect 0";
        List<CommandResult> commandResults = AlloyUtils.runAlloyString(alloy, false);
        assertEquals("unsat", commandResults.get(0).satResult);
    }
    
    @Test
    public void no_OneA_with_several_A() throws Exception
    {
        String alloy = alloySignatures + "check { no x : OneA | #(x.a) > 1 } expect 0";
        List<CommandResult> commandResults = AlloyUtils.runAlloyString(alloy, false);
        assertEquals("unsat", commandResults.get(0).satResult);
    }
}

