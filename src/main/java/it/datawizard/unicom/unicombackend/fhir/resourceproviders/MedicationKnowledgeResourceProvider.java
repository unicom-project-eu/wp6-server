package it.datawizard.unicom.unicombackend.fhir.resourceproviders;

import ca.uhn.fhir.i18n.Msg;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.QuantityParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
import com.github.mustachejava.Code;
import it.datawizard.unicom.unicombackend.jpa.entity.AtcCode;
import it.datawizard.unicom.unicombackend.jpa.entity.Ingredient;
import it.datawizard.unicom.unicombackend.jpa.entity.MedicinalProduct;
import it.datawizard.unicom.unicombackend.jpa.entity.PackagedMedicinalProduct;
import it.datawizard.unicom.unicombackend.jpa.entity.edqm.EdqmPackageItemType;
import it.datawizard.unicom.unicombackend.jpa.repository.MedicinalProductRepository;
import it.datawizard.unicom.unicombackend.jpa.repository.PackagedMedicinalProductRepository;
import it.datawizard.unicom.unicombackend.jpa.specification.MedicinalProductSpecifications;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IPrimitiveType;
import org.hl7.fhir.r4b.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Stream;

@Component
public class MedicationKnowledgeResourceProvider implements IResourceProvider {

    private static Logger LOG = LoggerFactory.getLogger(MedicationKnowledgeResourceProvider.class);
    final private MedicinalProductRepository medicinalProductRepository;
    final private PlatformTransactionManager platformTransactionManager;
    final private TransactionTemplate transactionTemplate;

    @Autowired
    public MedicationKnowledgeResourceProvider(MedicinalProductRepository medicinalProductRepository, PlatformTransactionManager platformTransactionManager) {
        this.medicinalProductRepository = medicinalProductRepository;
        this.platformTransactionManager = platformTransactionManager;
        transactionTemplate = new TransactionTemplate(this.platformTransactionManager);
    }


    @Override
    public Class<MedicationKnowledge> getResourceType() {
        return MedicationKnowledge.class;
    }

    @Read()
    @Transactional
    public MedicationKnowledge getResourceById(RequestDetails requestDetails, @IdParam IdType id) {
        Optional<MedicinalProduct> result = medicinalProductRepository
                .findByIdAndCountry(id.getIdPartAsLong(), requestDetails.getTenantId());
        return result.map(MedicationKnowledgeResourceProvider::medicationKnowledgeFromEntity).orElse(null);
    }

    @Search
    @Transactional
    public IBundleProvider
    findResources(
            RequestDetails requestDetails,
            @OptionalParam(name = MedicationKnowledge.SP_CODE) StringParam code,
            @OptionalParam(name = MedicationKnowledge.SP_STATUS) StringParam status,
            @OptionalParam(name = MedicationKnowledge.SP_MANUFACTURER) StringParam manufacturer,
            @OptionalParam(name = MedicationKnowledge.SP_DOSEFORM) StringParam authorizedPharmaceuticalDoseForm,
            @OptionalParam(name = MedicationKnowledge.SP_MONOGRAPH) ReferenceParam monographReference,
            @OptionalParam(name = MedicationKnowledge.SP_MONOGRAPH_TYPE) StringParam monographType,
            @OptionalParam(name = MedicationKnowledge.SP_INGREDIENT) ReferenceParam ingredientReference,
            @OptionalParam(name = MedicationKnowledge.SP_SOURCE_COST) StringParam sourceCost,
            @OptionalParam(name = MedicationKnowledge.SP_MONITORING_PROGRAM_NAME) StringParam monitoringProgramName,
            @OptionalParam(name = MedicationKnowledge.SP_MONITORING_PROGRAM_TYPE) StringParam monitoringProgramType,
            @OptionalParam(name = MedicationKnowledge.SP_CLASSIFICATION) StringParam classification,
            @OptionalParam(name = MedicationKnowledge.SP_CLASSIFICATION_TYPE) StringParam classificationType,
            @OptionalParam(name = MedicationKnowledge.SP_INGREDIENT_CODE) StringParam ingredientCode

            ) {
        final String tenantId = requestDetails.getTenantId();
        final InstantType searchTime = InstantType.withCurrentTime();

        handleReferenceParamsExceptions(ingredientReference, monographReference);


        Specification<MedicinalProduct> specification = Specification
                .where(tenantId != null ? MedicinalProductSpecifications.isCountryEqualTo(tenantId) : null)
                .and(classification != null ? MedicinalProductSpecifications.atcCodesContains(classification.getValue()): null)
                .and(ingredientReference != null ? MedicinalProductSpecifications.ingredientsContain(ingredientReference.getIdPart()): null)
                .and(ingredientCode != null ? MedicinalProductSpecifications.isSubstanceCodeEqualTo(ingredientCode.getValue()): null)
                .and(authorizedPharmaceuticalDoseForm != null ? MedicinalProductSpecifications.isAuthorizedPharmaceuticalDoseFormEqualTo(authorizedPharmaceuticalDoseForm.getValue()) : null);

        return new IBundleProvider() {
            final boolean shouldReturnEmptyResult =
                    Stream.of(
                            code,
                            manufacturer,
                            monitoringProgramName,
                            monitoringProgramType,
                            monographReference,
                            monographType,
                            sourceCost,
                            status).filter(bp -> bp != null).count() > 0
                    || (classificationType != null && !classificationType.getValue().equals("200000025916"));

            @Override
            public Integer size() {
                return shouldReturnEmptyResult? 0 : (int)medicinalProductRepository.findAll(specification,PageRequest.of(1,1)).getTotalElements();
            }

            @Nonnull
            @Override
            public List<IBaseResource> getResources(int theFromIndex, int theToIndex) {
                final int pageSize = theToIndex-theFromIndex;
                final int currentPageIndex = theFromIndex/pageSize;

                final List<IBaseResource> results = new ArrayList<>();

                if (!shouldReturnEmptyResult) {
                    transactionTemplate.execute(status -> {
                        Page<MedicinalProduct> allMedicinalProducts = medicinalProductRepository.findAll(specification, PageRequest.of(currentPageIndex,pageSize));
                        results.addAll(allMedicinalProducts.stream()
                                .map(MedicationKnowledgeResourceProvider::medicationKnowledgeFromEntity)
                                .toList());
                        return null;
                    });
                }
                return results;
            }

            @Override
            public InstantType getPublished() {
                return searchTime;
            }

            @Override
            public Integer preferredPageSize() {
                // Typically this method just returns null
                return null;
            }

            @Override
            public String getUuid() {
                return null;
            }
        };
    }

    public static MedicationKnowledge medicationKnowledgeFromEntity(MedicinalProduct entityMedicinalProduct) {
        MedicinalProductDefinition medicinalProductDefinition = MedicinalProductDefinitionResourceProvider
                .medicinalProductDefinitionFromEntity(entityMedicinalProduct);

        MedicationKnowledge medicationKnowledge = new MedicationKnowledge();

        medicationKnowledge.setId(entityMedicinalProduct.getId().toString());

        //Classification
        MedicationKnowledge.MedicationKnowledgeMedicineClassificationComponent medicationKnowledgeMedicineClassificationComponent = medicationKnowledge.addMedicineClassification();
        medicationKnowledgeMedicineClassificationComponent.getClassification().addAll(medicinalProductDefinition.getClassification());
        //Classification type
        medicationKnowledgeMedicineClassificationComponent.setType(medicinalProductDefinition.getType());

        // packaging
        MedicationKnowledge.MedicationKnowledgePackagingComponent packagingComponent =
                new MedicationKnowledge.MedicationKnowledgePackagingComponent();
        CodeableConcept packagingTypeCodeableConcept = new CodeableConcept();
        EdqmPackageItemType edqmPackageItemType =
                entityMedicinalProduct.getPackagedMedicinalProducts().iterator().next().getPackageItems()
                .iterator().next().getType();
        packagingTypeCodeableConcept.addCoding(
                "https://spor.ema.europa.eu/v1/lists/100000073346",//TODO: check link
                edqmPackageItemType.getCode(),
                edqmPackageItemType.getTerm()
        );

        packagingComponent.setType(packagingTypeCodeableConcept);
        packagingComponent.setQuantity(null);

        medicationKnowledge.setPackaging(packagingComponent);


        // ingredient
        List<MedicationKnowledge.MedicationKnowledgeIngredientComponent> ingredients = new ArrayList<>();
        entityMedicinalProduct.getAllIngredients().forEach(ingredient -> {
            MedicationKnowledge.MedicationKnowledgeIngredientComponent ingredientComponent
                    = new MedicationKnowledge.MedicationKnowledgeIngredientComponent();

            Reference ingredientReference = new Reference(SubstanceResourceProvider.substanceFromEntity(ingredient));
            ingredientComponent.setItem(ingredientReference);

            ingredients.add(ingredientComponent);
        });

        medicationKnowledge.setIngredient(ingredients);

        // doseForm
        CodeableConcept doseFormCodeableConcept = new CodeableConcept();
        doseFormCodeableConcept.addCoding(
                    "https://spor.ema.europa.eu/v1/lists/200000000004",
                    entityMedicinalProduct.getAuthorizedPharmaceuticalDoseForm().getCode(),
                    entityMedicinalProduct.getAuthorizedPharmaceuticalDoseForm().getTerm()
                );
        medicationKnowledge.setDoseForm(doseFormCodeableConcept);

        // intendedRoute
        List<CodeableConcept> intendedRoutes = new LinkedList<>();
        entityMedicinalProduct.getPharmaceuticalProduct().getRoutesOfAdministration()
                .forEach(edqmRouteOfAdministration -> {
                    CodeableConcept intendedRouteCodebleConcept = new CodeableConcept();
                    intendedRouteCodebleConcept.addCoding(
                            "https://spor.ema.europa.eu/v1/lists/100000073345",
                            edqmRouteOfAdministration.getCode(),
                            edqmRouteOfAdministration.getTerm()
                        );
                    intendedRoutes.add(intendedRouteCodebleConcept);
        });
        medicationKnowledge.setIntendedRoute(intendedRoutes);

        return medicationKnowledge;
    }

    private void handleReferenceParamsExceptions(ReferenceParam ingredientReference, ReferenceParam monographReference) {
        if (ingredientReference != null && ingredientReference.hasResourceType()) {
            String ingredientReferenceResourceType = ingredientReference.getResourceType();
            if (!"Ingredient".equals(ingredientReferenceResourceType)) {
                throw new InvalidRequestException(Msg.code(633) + "Invalid resource type for parameter 'ingredient': " + ingredientReferenceResourceType);
            }
        }

        if (monographReference != null && monographReference.hasResourceType()) {
            String monographReferenceResourceType = monographReference.getResourceType();
            if (!"Monograph".equals(monographReferenceResourceType)) {
                throw new InvalidRequestException(Msg.code(633) + "Invalid resource type for parameter 'substance': " + monographReferenceResourceType);
            }
        }
    }
}
