package io.swagger.codegen.languages;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import io.swagger.codegen.*;
import io.swagger.models.*;
import io.swagger.util.Yaml;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/* TODO: Introduce AbstractTypeScriptNodeCodegen base class */
public class TypeScriptNodeServerCodegen extends TypeScriptNodeClientCodegen implements CodegenConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeScriptNodeServerCodegen.class);
    public static final String SERVER_PORT = "serverPort";
    public static final String DEFAULT_SOURCE_FOLDER = "src";

    protected String apiVersion = "1.0.0";
    protected String projectName = "swagger-server";
    protected String defaultServerPort = "8080";
    protected String sourceFolder = DEFAULT_SOURCE_FOLDER;

    public TypeScriptNodeServerCodegen() {
        super();

        typeMapping.put("file", "Buffer");

        outputFolder = "generated-code" + File.separator + "typescript-node-server";

        apiTemplateFiles.put("controller.mustache", ".ts");
        // apiTemplateFiles.put("service.mustache", "Service.ts");
        // modelTemplateFiles.put("model.mustache", ".ts");

        embeddedTemplateDir = templateDir = "typescript-node-server";

        additionalProperties.put("apiVersion", apiVersion);
        
        cliOptions.add(new CliOption(SERVER_PORT, "TCP port to listen on."));
        cliOptions.add(new CliOption(CodegenConstants.SOURCE_FOLDER, CodegenConstants.SOURCE_FOLDER_DESC).defaultValue(DEFAULT_SOURCE_FOLDER));
    }

    @Override
    public void processOpts() {
        super.processOpts();
        
        // FIXME: we should not be inheriting the client class
        supportingFiles.removeIf(new Predicate<SupportingFile>() {
			@Override
			public boolean test(SupportingFile sf) {
				return sf.templateFile.equals("api.mustache");
			}
		});
        
        if (npmName != null) {
        	supportingFiles.add(new SupportingFile("npmrc", getPackageRootDirectory(), ".npmrc"));        	
        }
        
        if (additionalProperties.containsKey(CodegenConstants.SOURCE_FOLDER)) {
            this.sourceFolder = ((String) additionalProperties.get(CodegenConstants.SOURCE_FOLDER)).replace("/", File.separator);
        }       

        supportingFiles.add(new SupportingFile("models-index.mustache", this.sourceFolder + File.separator + "models", "index.ts"));
        supportingFiles.add(new SupportingFile("services-index.mustache", this.sourceFolder + File.separator + "services", "index.ts"));
        supportingFiles.add(new SupportingFile("writer.mustache", this.sourceFolder + File.separator + "utils", "writer.ts"));
        supportingFiles.add(new SupportingFile("swagger.mustache", this.sourceFolder + File.separator + "api", "swagger.yaml"));
        
        writeOptional(outputFolder, new SupportingFile("index.mustache", this.sourceFolder, "index.ts"));
        writeOptional(outputFolder, new SupportingFile("client.mustache", this.sourceFolder, "client.ts"));
        writeOptional(outputFolder, new SupportingFile("server.mustache", this.sourceFolder, "server.ts"));
        writeOptional(outputFolder, new SupportingFile("README.mustache", "", "README.md"));
        writeOptional(outputFolder, new SupportingFile("prettierrc.mustache", "", ".prettierrc"));        
    }    
    
    @Override
    public String apiPackage() {
        return "controllers";
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.SERVER;
    }

    @Override
    public String getName() {
        return "typescript-node-server";
    }

    @Override
    public String getHelp() {
        return "Generates a typescript/nodejs server library using the swagger-tools project.";
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultController";
        }
        return initialCaps(name);
    }

    @Override
    public String toApiFilename(String name) {
        return toApiName(name);
    }

    /*
    @Override
    public String apiFilename(String templateName, String tag) {
        String result = super.apiFilename(templateName, tag);

        if (templateName.equals("service.mustache")) {
        	LOGGER.warn(result);
        	// Paths.get(result).iterator()
            String stringToMatch = File.separator + "controllers" + File.separator;
            String replacement = File.separator + implFolder + File.separator;
        	LOGGER.warn(" => stringToMatch = " + Pattern.quote(stringToMatch));
        	LOGGER.warn(" => replacement = " + replacement);
            result = result.replaceAll(Pattern.quote(stringToMatch), replacement);
            LOGGER.warn(" => " + result);
        }
        return result;
    }
    */
    
    @Override
    public String apiFileFolder() {
        return outputFolder + File.separator + "src" + File.separator + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separator + "src" + File.separator + "models" + File.separator + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
        @SuppressWarnings("unchecked")
        Map<String, Object> objectMap = (Map<String, Object>) objs.get("operations");
        @SuppressWarnings("unchecked")
        List<CodegenOperation> operations = (List<CodegenOperation>) objectMap.get("operation");
        for (CodegenOperation operation : operations) {
            operation.httpMethod = operation.httpMethod.toLowerCase();

            List<CodegenParameter> params = operation.allParams;
            if (params != null && params.size() == 0) {
                operation.allParams = null;
            }
            List<CodegenResponse> responses = operation.responses;
            if (responses != null) {
                for (CodegenResponse resp : responses) {
                    if ("0".equals(resp.code)) {
                        resp.code = "default";
                    }
                }
            }
            if (operation.examples != null && !operation.examples.isEmpty()) {
                // Leave application/json* items only
                for (Iterator<Map<String, String>> it = operation.examples.iterator(); it.hasNext(); ) {
                    final Map<String, String> example = it.next();
                    final String contentType = example.get("contentType");
                    if (contentType == null || !contentType.startsWith("application/json")) {
                        it.remove();
                    }
                }
            }
        }
        return objs;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> getOperations(Map<String, Object> objs) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        Map<String, Object> apiInfo = (Map<String, Object>) objs.get("apiInfo");
        List<Map<String, Object>> apis = (List<Map<String, Object>>) apiInfo.get("apis");
        for (Map<String, Object> api : apis) {
            result.add((Map<String, Object>) api.get("operations"));
        }
        return result;
    }

    private static List<Map<String, Object>> sortOperationsByPath(List<CodegenOperation> ops) {
        Multimap<String, CodegenOperation> opsByPath = ArrayListMultimap.create();

        for (CodegenOperation op : ops) {
            opsByPath.put(op.path, op);
        }

        List<Map<String, Object>> opsByPathList = new ArrayList<Map<String, Object>>();
        for (Entry<String, Collection<CodegenOperation>> entry : opsByPath.asMap().entrySet()) {
            Map<String, Object> opsByPathEntry = new HashMap<String, Object>();
            opsByPathList.add(opsByPathEntry);
            opsByPathEntry.put("path", entry.getKey());
            opsByPathEntry.put("operation", entry.getValue());
            List<CodegenOperation> operationsForThisPath = Lists.newArrayList(entry.getValue());
            operationsForThisPath.get(operationsForThisPath.size() - 1).hasMore = false;
            if (opsByPathList.size() < opsByPath.asMap().size()) {
                opsByPathEntry.put("hasMore", "true");
            }
        }

        return opsByPathList;
    }

    @Override
    public void preprocessSwagger(Swagger swagger) {
        String host = swagger.getHost();
        String port = defaultServerPort;

        if (!StringUtils.isEmpty(host)) {
            String[] parts = host.split(":");
            if (parts.length > 1) {
                port = parts[1];
            }
        } else {
            // host is empty, default to https://localhost
            host = "http://localhost";
            LOGGER.warn("'host' in the specification is empty or undefined. Default to http://localhost.");
        }

        if (additionalProperties.containsKey(SERVER_PORT)) {
            port = additionalProperties.get(SERVER_PORT).toString();
        }
        this.additionalProperties.put(SERVER_PORT, port);

        if (swagger.getInfo() != null) {
            Info info = swagger.getInfo();
            if (info.getTitle() != null) {
                // when info.title is defined, use it for projectName
                // used in package.json
                projectName = info.getTitle()
                        .replaceAll("[^a-zA-Z0-9]", "-")
                        .replaceAll("^[-]*", "")
                        .replaceAll("[-]*$", "")
                        .replaceAll("[-]{2,}", "-")
                        .toLowerCase();
                this.additionalProperties.put("projectName", projectName);
            }
        }

        // need vendor extensions for x-swagger-router-controller
        Map<String, Path> paths = swagger.getPaths();
        if (paths != null) {
            for (String pathname : paths.keySet()) {
                Path path = paths.get(pathname);
                Map<HttpMethod, Operation> operationMap = path.getOperationMap();
                if (operationMap != null) {
                    for (HttpMethod method : operationMap.keySet()) {
                        Operation operation = operationMap.get(method);
                        String tag = "default";
                        if (operation.getTags() != null && operation.getTags().size() > 0) {
                            tag = toApiName(operation.getTags().get(0));
                        }
                        if (operation.getOperationId() == null) {
                            operation.setOperationId(getOrGenerateOperationId(operation, pathname, method.toString()));
                        }
                        if (operation.getVendorExtensions().get("x-swagger-router-controller") == null) {
                            operation.getVendorExtensions().put("x-swagger-router-controller", sanitizeTag(tag));
                        }
                    }
                }
            }
        }
    }

    @Override
    public Map<String, Object> postProcessSupportingFileData(Map<String, Object> objs) {
        Swagger swagger = (Swagger)objs.get("swagger");
        if (swagger != null) {
            try {
                SimpleModule module = new SimpleModule();
                module.addSerializer(Double.class, new JsonSerializer<Double>() {
                    @Override
                    public void serialize(Double val, JsonGenerator jgen,
                                          SerializerProvider provider) throws IOException, JsonProcessingException {
                        jgen.writeNumber(new BigDecimal(val));
                    }
                });
                objs.put("swagger-yaml", Yaml.mapper().registerModule(module).writeValueAsString(swagger));
            } catch (JsonProcessingException e) {
                LOGGER.error(e.getMessage(), e);
            }
        }
        for (Map<String, Object> operations : getOperations(objs)) {
            @SuppressWarnings("unchecked")
            List<CodegenOperation> ops = (List<CodegenOperation>) operations.get("operation");

            List<Map<String, Object>> opsByPathList = sortOperationsByPath(ops);
            operations.put("operationsByPath", opsByPathList);
        }
        return super.postProcessSupportingFileData(objs);
    }
}
