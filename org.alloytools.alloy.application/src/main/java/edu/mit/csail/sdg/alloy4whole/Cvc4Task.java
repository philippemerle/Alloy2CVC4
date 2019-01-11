package edu.mit.csail.sdg.alloy4whole;

import edu.mit.csail.sdg.alloy4.*;

import edu.mit.csail.sdg.ast.Command;
import edu.uiowa.alloy2smt.Utils;
import edu.uiowa.alloy2smt.mapping.Mapper;
import edu.uiowa.alloy2smt.mapping.MappingField;
import edu.uiowa.alloy2smt.mapping.MappingSignature;
import edu.uiowa.alloy2smt.smtAst.*;
import edu.uiowa.alloy2smt.smtparser.SmtModelVisitor;
import edu.uiowa.alloy2smt.smtparser.antlr.SmtLexer;
import edu.uiowa.alloy2smt.smtparser.antlr.SmtParser;
import edu.uiowa.alloy2smt.translators.Translation;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import edu.mit.csail.sdg.alloy4whole.instances.*;

import static edu.mit.csail.sdg.alloy4.A4Preferences.Cvc4Timeout;

public class Cvc4Task implements WorkerEngine.WorkerTask
{
    public static final String tempDirectory        = System.getProperty("java.io.tmpdir");
    private static final String TIMEOUT_OPTION       = "tlimit" ;

    private final Map<String, String>   alloyFiles;
    private final String                originalFileName;
    private final int                   resolutionMode;
    private final int                   targetCommandIndex;

    // only one process for alloy editor
    public static Cvc4Process cvc4Process;
    private WorkerEngine.WorkerCallback workerCallback;
    private Translation translation;
    public static String lastXmlFile;

    Cvc4Task(Map<String, String> alloyFiles, String originalFileName, int resolutionMode, int targetCommandIndex)
    {
        this.alloyFiles         = alloyFiles;
        this.originalFileName   = originalFileName;
        this.resolutionMode     = resolutionMode;
        this.targetCommandIndex = targetCommandIndex;
    }
    @Override
    public void run(WorkerEngine.WorkerCallback workerCallback) throws Exception
    {
        try
        {
            this.workerCallback = workerCallback;

            final long startTranslate = System.currentTimeMillis();

            translation = translateToSMT();

            final long endTranslate = System.currentTimeMillis();

            callbackBold("Translation time: " + (endTranslate - startTranslate) + " ms\n");

            String smtScript = translation.getSmtScript();

            if (smtScript != null)
            {
                cvc4Process = Cvc4Process.start(workerCallback);

                cvc4Process.sendCommand(smtScript);

                String options =  setSolverOptions(cvc4Process, translation);

                callbackPlain(options);

                // execute all commands if targetCommandIndex < 0
                if(targetCommandIndex < 0)
                {
                    // surround each command except the last one with (push) and (pop)
                    for (int index = 0; index < translation.getCommands().size() - 1; index++) {
                        // (push)
                        cvc4Process.sendCommand(Translation.PUSH);
                        solveCommand(index);
                        // (pop)
                        cvc4Process.sendCommand(Translation.POP);
                    }

                    // solve the last command without push and pop to view multiple models if sat
                    lastXmlFile = solveCommand(translation.getCommands().size() - 1);
                }
                else// execute only the target command
                {
                    // solve the target command without push and pop to view multiple models if sat
                    lastXmlFile = solveCommand(translation.getCommands().size() - 1);
                }

                //ToDo: review when to destroy the process
                //cvc4Process.destroy();
            }
            else
            {
                callbackPlain("No translation found from alloy model to SMT");
            }
        }
        catch (Exception exception)
        {
            StringWriter stringWriter = new StringWriter();
            exception.printStackTrace(new PrintWriter(stringWriter));
            throw new Exception(stringWriter.toString());
        }
    }

    public static String setSolverOptions(Cvc4Process cvc4Process, Translation translation) throws IOException
    {
        Map<String, String> options = new HashMap<>();

        // (set-option :tlimit 30000)
        options.put(TIMEOUT_OPTION, Cvc4Timeout.get().toString());

        String script = translation.translateOptions(options);
        cvc4Process.sendCommand(script);

        return script;
    }

    private String solveCommand(int index) throws Exception
    {
        String commandTranslation = translation.translateCommand(index);

        Command command = translation.getCommands().get(index);

        callbackBold("Executing " + command.label + "\n");

        ErrorWarning warning = new ErrorWarning(command.pos, "The scope in " + command +
                " is ignored by cvc4");

        callbackWarning(warning);

        final long startSolve   = System.currentTimeMillis();

        // (check-sat)
        callbackPlain( commandTranslation + Translation.CHECK_SAT);
        String result = cvc4Process.sendCommand(commandTranslation + Translation.CHECK_SAT);

        final long endSolve     = System.currentTimeMillis();
        long duration		    = (endSolve - startSolve);
        callbackBold("Solving time: " + duration + " ms\n");

        callbackBold("Satisfiability: " + result + "\n");

        if(result != null)
        {
            switch (result)
            {
                case "sat":
                    return prepareInstance(index, duration);
                case "unsat":
                    if(translation.getCommands().get(index).check)
                    {
                        callbackPlain("The assertion is valid\n");
                    }
                    else
                    {
                        callbackPlain("The result is unsat\n");
                    }
                    return null;
                default:
                    callbackPlain("The result is unknown\n");
                    return null;
            }
        }
        else
        {
            callbackPlain("No result returned from cvc4\n");
            return null;
        }
    }

    private void callbackPlain(String log)
    {
        workerCallback.callback(new Object[]{"", log});
        workerCallback.callback(new Object[]{"", ""});
    }

    private void callbackBold(String log)
    {
        workerCallback.callback(new Object[]{"S2", "\n"});
        workerCallback.callback(new Object[]{"S2", log});
        workerCallback.callback(new Object[]{"S2", "\n"});
    }

    private void callbackWarning(ErrorWarning warning)
    {
        workerCallback.callback(new Object[]{"warning", warning});
    }


    private String prepareInstance(int commandIndex, long duration) throws Exception
    {
        String smtModel = cvc4Process.sendCommand(Translation.GET_MODEL);

        callbackPlain("A model has been found\n");
        callbackPlain(smtModel);

        Command command = translation.getCommands().get(commandIndex);
        SmtModel model = parseModel(smtModel);

        File xmlFile        = File.createTempFile("tmp", ".smt.xml", new File(tempDirectory));

        String xmlFilePath  = xmlFile.getAbsolutePath();

        writeModelToAlloyXmlFile(translation.getMapper(), model, xmlFilePath, originalFileName, command, alloyFiles);

        callbackBold("Generated alloy instance file: " + xmlFilePath +"\n");

        String  satResult           = "sat";

        Object[] message            = new Object []{satResult, command.check, command.expects, xmlFilePath, command.toString(), duration};
        workerCallback.callback(message);

        return xmlFilePath;
    }

    public static void writeModelToAlloyXmlFile(Mapper mapper, SmtModel model, String xmlFileName,
              String originalFileName, Command command, Map<String, String> alloyFiles) throws Exception
    {

        Map<String, FunctionDefinition> functionsMap = new HashMap<>();

        for(FunctionDefinition function: model.getFunctionDefinitions())
        {
            functionsMap.put(function.funcName, function);
        }

        List<Signature> signatures = new ArrayList<>();

        for (MappingSignature mappingSignature : mapper.signatures )
        {
            Signature signature = getSignature(functionsMap, mappingSignature);
            signatures.add(signature);
        }

        List<Field> fields = new ArrayList<>();

        for (MappingField mappingField : mapper.fields )
        {
            Field field = getField(functionsMap, mappingField);
            fields.add(field);
        }

        Instance instance   = new Instance();
        instance.signatures = signatures;
        instance.fields     = fields;
        instance.bitWidth   = 4;
        instance.maxSeq     = 4; //ToDo: find an appropriate value for maxSeq and bitWidth
        instance.command    = command.toString();

        instance.fileName = originalFileName;

        Alloy alloy = new Alloy();
        alloy.instances = new ArrayList<>();
        alloy.instances.add(instance);
        alloy.buildDate = java.time.Instant.now().toString();
        alloy.alloyFiles = new ArrayList<>();

        for (Map.Entry<String, String> entry : alloyFiles.entrySet())
        {
            alloy.alloyFiles.add(new AlloyFile(entry.getKey(), entry.getValue()));
        }

        alloy.writeToXml(xmlFileName);
    }

    private static Signature getSignature(Map<String, FunctionDefinition> functionsMap, MappingSignature mappingSignature) throws Exception
    {
        Signature signature  = new Signature();

        // get signature info from the mapping
        signature.label         = mappingSignature.label;
        signature.id            = mappingSignature.id;
        signature.parentId  	= mappingSignature.parentId;

        signature.builtIn       = mappingSignature.builtIn ? "yes" : "no";
        signature.isAbstract    = mappingSignature.isAbstract? "yes" : "no";
        signature.isOne         = mappingSignature.isOne? "yes" : "no";
        signature.isLone        = mappingSignature.isLone? "yes" : "no";
        signature.isSome        = mappingSignature.isSome? "yes" : "no";
        signature.isPrivate     = mappingSignature.isPrivate? "yes" : "no";
        signature.isMeta        = mappingSignature.isMeta? "yes" : "no";
        signature.isExact   	= mappingSignature.isExact ? "yes" : "no";
        signature.isEnum        = mappingSignature.isEnum? "yes" : "no";

        // get the corresponding function from the model
        FunctionDefinition function = functionsMap.get(mappingSignature.functionName);
        if(function == null)
        {
            throw new Exception("Can not find the function "+ mappingSignature.functionName
                    + " for signature "+ signature.label + "in the model.") ;
        }

        signature.atoms = getAtoms(function.expression);
        return signature;
    }

    private static Field getField(Map<String,FunctionDefinition> functionsMap, MappingField mappingField) throws Exception
    {
        Field field  = new Field();

        // get field info from the mapping
        field.label         = mappingField.label;
        field.id            = mappingField.id;
        field.parentId  	= mappingField.parentId;

        field.isPrivate     = mappingField.isPrivate? "yes" : "no";
        field.isMeta        = mappingField.isMeta? "yes" : "no";

        // get the corresponding function from the model
        FunctionDefinition function = functionsMap.get(mappingField.functionName);
        if(function == null)
        {
            throw new Exception("Can not find the function "+ mappingField.functionName
                    + " for field "+ field.label + "in the model.") ;
        }

        field.tuples = getTuples(function.expression);
        field.types  = getTypes(mappingField);

        return field;
    }

    private static Types getTypes(MappingField mappingField)
    {
        Types types = new Types();

        types.types = mappingField.types.stream()
                .map(t -> new Type(t.id)).collect(Collectors.toList());
        return types;
    }

    private static List<Tuple> getTuples(Expression expression)
    {
        List<Tuple> tuples = new ArrayList<>();

        if(expression instanceof  UnaryExpression)
        {
            UnaryExpression unary = (UnaryExpression) expression;
            switch (unary.getOP())
            {
                case EMPTYSET: return new ArrayList<>();
                case SINGLETON:
                {
                    Expression unaryExpression = unary.getExpression();
                    if(unaryExpression instanceof MultiArityExpression)
                    {
                        MultiArityExpression multiArity = (MultiArityExpression) unaryExpression;

                        if(multiArity.getOp() == MultiArityExpression.Op.MKTUPLE)
                        {
                            List<Atom> atoms    = getAtoms(multiArity);
                            Tuple tuple         = new Tuple();
                            tuple.atoms         = atoms;
                            return Collections.singletonList(tuple);
                        }

                        throw new UnsupportedOperationException();
                    }
                    throw new UnsupportedOperationException();
                }
                default:
                    throw new UnsupportedOperationException();
            }
        }

        if(expression instanceof  BinaryExpression)
        {
            BinaryExpression binary = (BinaryExpression) expression;

            switch (binary.getOp())
            {
                case UNION:
                {
                    tuples.addAll(getTuples(binary.getLhsExpr()));
                    tuples.addAll(getTuples(binary.getRhsExpr()));
                    return tuples;
                }
                default:
                    throw new UnsupportedOperationException();
            }
        }

        throw new UnsupportedOperationException();
    }

    private static List<Atom> getAtoms(Expression expression)
    {
        List<Atom> atoms = new ArrayList<>();


        if(expression instanceof  AtomConstant)
        {
            AtomConstant atomConstant = (AtomConstant) expression;
            atoms.add(new Atom(atomConstant.getName()));
            return  atoms;
        }

        if(expression instanceof  UnaryExpression)
        {
            UnaryExpression unary = (UnaryExpression) expression;
            switch (unary.getOP())
            {
                case EMPTYSET: return new ArrayList<>();
                case SINGLETON:
                    {
                        return getAtoms(unary.getExpression());
                    }
                default:
                    throw new UnsupportedOperationException();
            }
        }

        if(expression instanceof  BinaryExpression)
        {
            BinaryExpression binary = (BinaryExpression) expression;

            switch (binary.getOp())
            {
                case UNION:
                {
                    atoms.addAll(getAtoms(binary.getLhsExpr()));
                    atoms.addAll(getAtoms(binary.getRhsExpr()));
                    return atoms;
                }
                default:
                    throw new UnsupportedOperationException();
            }
        }

        if(expression instanceof MultiArityExpression)
        {
            MultiArityExpression multiArity = (MultiArityExpression) expression;
            switch (multiArity.getOp())
            {
                case MKTUPLE:
                {
                    for (Expression expr: multiArity.getExpressions())
                    {
                        atoms.addAll(getAtoms(expr));
                    }

                    return atoms;
                }

                default:
                    throw new UnsupportedOperationException();
            }
        }

        throw new UnsupportedOperationException();
    }

    private Translation translateToSMT() throws IOException
    {
        Translation translation = Utils.translate(alloyFiles, originalFileName, resolutionMode);

        callbackBold("Translation output");
        callbackPlain(translation.getSmtScript());

        File jsonFile = File.createTempFile("tmp", ".mapping.json", new File(tempDirectory));
        // output the mapping

        translation.getMapper().writeToJson(jsonFile.getPath());

        callbackPlain("Generated a mapping file: " + jsonFile.getAbsolutePath() +"\n");

        return translation;
    }

    public static SmtModel parseModel(String model)
    {
        CharStream charStream = CharStreams.fromString(model);

        SmtLexer lexer = new SmtLexer(charStream);
        CommonTokenStream tokenStream = new CommonTokenStream(lexer);
        SmtParser parser = new SmtParser(tokenStream);

        ParseTree tree =  parser.model();
        SmtModelVisitor visitor = new SmtModelVisitor();

        SmtModel smtModel = (SmtModel) visitor.visit(tree);

        return  smtModel;
    }
}
