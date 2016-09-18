package org.graylog.plugins.threatintel.pipelines.functions;

import com.google.inject.Inject;
import org.graylog.plugins.pipelineprocessor.EvaluationContext;
import org.graylog.plugins.pipelineprocessor.ast.expressions.Expression;
import org.graylog.plugins.pipelineprocessor.ast.functions.Function;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionArgs;
import org.graylog.plugins.pipelineprocessor.ast.functions.FunctionDescriptor;
import org.graylog.plugins.pipelineprocessor.ast.functions.ParameterDescriptor;
import org.graylog.plugins.threatintel.providers.otx.OTXDomainLookupProvider;
import org.graylog.plugins.threatintel.providers.otx.OTXIntel;
import org.graylog.plugins.threatintel.providers.otx.OTXLookupResult;
import org.graylog2.plugin.LocalMetricRegistry;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OTXDomainLookupFunction implements Function<OTXLookupResult> {

    private static final Logger LOG = LoggerFactory.getLogger(OTXDomainLookupFunction.class);

    public static final String NAME = "otx_lookup_domain";
    private static final String VALUE = "domain_name";

    private final ParameterDescriptor<String, String> valueParam = ParameterDescriptor.string(VALUE).description("The domain to look up. Example: foo.example.org (A trailing dot ('.') will be ignored.)").build();

    private final OTXDomainLookupProvider provider;

    private final LocalMetricRegistry metrics;

    @Inject
    public OTXDomainLookupFunction(final ClusterConfigService clusterConfigService,
                                   final LocalMetricRegistry localRegistry) {
        // meh meh meh
        OTXDomainLookupProvider.getInstance().initialize(clusterConfigService, localRegistry);

        this.provider = OTXDomainLookupProvider.getInstance();
        this.metrics = localRegistry;
    }

    private OTXDomainLookupFunction() {
        this.provider = null;
        this.metrics = null;
    }

    /**
     * Useful for testing.
     *
     * @return the function but without an initialized lookup provider or any dependencies.
     */
    public static OTXDomainLookupFunction buildStateless() {
        return new OTXDomainLookupFunction();
    }

    @Override
    public Object preComputeConstantArgument(FunctionArgs args, String s, Expression arg) {
        return arg.evaluateUnsafe(EvaluationContext.emptyContext());
    }

    @Override
    public OTXLookupResult evaluate(FunctionArgs args, EvaluationContext context) {
        String domain = valueParam.required(args, context);
        if (domain == null) {
            LOG.error("NULL parameter passed to OTX threat intel lookup.");
            return null;
        }

        domain = prepareDomain(domain);

        LOG.debug("Running OTX lookup for domain [{}].", domain);

        try {
            OTXIntel result = provider.lookup(domain);

            // It might return null in case of missing or invalid configuration.
            if(result == null) {
                return OTXLookupResult.EMPTY;
            }

            return OTXLookupResult.buildFromIntel(result);
        } catch (Exception e) {
            LOG.error("Could not lookup OTX threat intelligence for domain [{}].", domain, e);
            return null;
        }
    }

    public String prepareDomain(String domain) {
        // A typical issue is regular expressions that also capture a whitespace at the beginning or the end.
        domain = domain.trim();

        // Some systems will capture DNS requests with a trailing '.'. Remove that for the lookup.
        if(domain.endsWith(".")) {
            domain = domain.substring(0, domain.length()-1);
        }

        return domain;
    }

    @Override
    public FunctionDescriptor<OTXLookupResult> descriptor() {
        return FunctionDescriptor.<OTXLookupResult>builder()
                .name(NAME)
                .description("Look up AlienVault OTX threat intelligence data for a domain name.")
                .params(valueParam)
                .returnType(OTXLookupResult.class)
                .build();
    }

}
