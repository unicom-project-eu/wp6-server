package it.datawizard.unicom.unicombackend.jpa.entity;

import it.datawizard.unicom.unicombackend.jpa.entity.edqm.EdqmDoseForm;
import it.datawizard.unicom.unicombackend.jpa.enums.EdqmUnitOfPresentation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Objects;
import java.util.Set;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class ManufacturedItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne
    @JoinColumn()
    @ToString.Exclude
    private EdqmDoseForm manufacturedDoseForm;

    @Enumerated(EnumType.STRING)
    private EdqmUnitOfPresentation unitOfPresentation;

    private Integer manufacturedItemQuantity;

    @OneToMany(mappedBy = "manufacturedItem")
    private Set<PackageItem> packageItems;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        ManufacturedItem that = (ManufacturedItem) o;
        return id != null && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

}
