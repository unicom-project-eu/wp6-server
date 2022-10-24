package it.datawizard.unicom.unicombackend.fhir.resourceproviders;

import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.IResourceProvider;
import it.datawizard.unicom.unicombackend.jpa.entity.MedicinalProduct;
import it.datawizard.unicom.unicombackend.jpa.entity.PackagedMedicinalProduct;
import it.datawizard.unicom.unicombackend.jpa.repository.PackagedMedicinalProductRepository;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r5.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Component
public class MedicationKnowledgeResourceProvider implements IResourceProvider {

    private static Logger LOG = LoggerFactory.getLogger(MedicationKnowledgeResourceProvider.class);

    final private PackagedMedicinalProductRepository packagedMedicinalProductRepository;
    final private PlatformTransactionManager platformTransactionManager;
    final private TransactionTemplate transactionTemplate;

    @Autowired
    public MedicationKnowledgeResourceProvider(PackagedMedicinalProductRepository PackagedMedicinalProductRepository, PlatformTransactionManager platformTransactionManager) {
        this.packagedMedicinalProductRepository = PackagedMedicinalProductRepository;
        this.platformTransactionManager = platformTransactionManager;
        transactionTemplate = new TransactionTemplate(this.platformTransactionManager);
    }


    @Override
    public Class<MedicationKnowledge> getResourceType() {
        return MedicationKnowledge.class;
    }

    @Read()
    public MedicationKnowledge getResourceById(RequestDetails requestDetails, @IdParam IdType id) {
        Optional<PackagedMedicinalProduct> result = packagedMedicinalProductRepository
                .findByIdAndMedicinalProduct_Country(id.getIdPartAsLong(), requestDetails.getTenantId());
        return result.map(MedicationKnowledgeResourceProvider::medicationKnowledgeFromEntity).orElse(null);
    }

    @Search()
    public IBundleProvider findAllResources(RequestDetails requestDetails) {
        final String tenantId = requestDetails.getTenantId();
        final InstantType searchTime = InstantType.withCurrentTime();

        return new IBundleProvider() {
            @Override
            public IPrimitiveType<Date> getPublished() {
                return searchTime;
            }

            @NotNull
            @Override
            public Integer size() {
                return (int) packagedMedicinalProductRepository
                        .findByMedicinalProduct_Country(tenantId, PageRequest.of(1, 1)).getTotalElements();
            }

            @NotNull
            @Override
            public List<IBaseResource> getResources(int theFromIndex, int theToIndex) {
                final int pageSize = theToIndex-theFromIndex;
                final int currentPageIndex = theFromIndex/pageSize;

                final List<IBaseResource> results = new ArrayList<>();

                transactionTemplate.execute(status -> {
                    Page<PackagedMedicinalProduct> allMedicinalProducts = packagedMedicinalProductRepository
                            .findByMedicinalProduct_Country(tenantId, PageRequest.of(currentPageIndex,pageSize));
                    results.addAll(allMedicinalProducts.stream()
                            .map(MedicationKnowledgeResourceProvider::medicationKnowledgeFromEntity)
                            .toList());
                    return null;
                });

                return results;
            }

            @Nullable
            @Override
            public String getUuid() {
                return null;
            }

            @Override
            public Integer preferredPageSize() {
                return null;
            }
        };
    }

    public static MedicationKnowledge medicationKnowledgeFromEntity(PackagedMedicinalProduct entityPackagedMedicinalProduct) {
        MedicationKnowledge medicationKnowledge = new MedicationKnowledge();

        medicationKnowledge.setId(entityPackagedMedicinalProduct.getId().toString());

        // packaging
        List<MedicationKnowledge.MedicationKnowledgePackagingComponent> packagingComponents = new ArrayList<>();
        MedicationKnowledge.MedicationKnowledgePackagingComponent packagingComponent
                = new MedicationKnowledge.MedicationKnowledgePackagingComponent();
        packagingComponent.setPackagedProduct(
                new Reference(
                        PackagedProductDefinitionResourceProvider
                                .packagedProductDefinitionFromEntity(entityPackagedMedicinalProduct)
                )
        );
        packagingComponents.add(packagingComponent);
        medicationKnowledge.setPackaging(packagingComponents);

        return medicationKnowledge;
    }
}
