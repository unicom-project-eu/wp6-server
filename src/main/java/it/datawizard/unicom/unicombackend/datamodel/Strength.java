package it.datawizard.unicom.unicombackend.datamodel;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Setter
@Getter
public class Strength {
    public enum ReferenceSubstance {
        unitOfPresentationBased,
        concentrationBased,
    }

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private Float numeratorValue;
    private Float denominatorValue;

    // TODO normalize unit decomposing it in unit and multiplier (eg: for ml separate "milli" from "liters")
    private String numeratorUnit;
    private String denominatorUnit;

    @Enumerated(EnumType.STRING)
    private ReferenceSubstance referenceSubstance;
}



