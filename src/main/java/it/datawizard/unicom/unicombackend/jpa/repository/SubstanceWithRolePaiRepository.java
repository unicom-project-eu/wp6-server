package it.datawizard.unicom.unicombackend.jpa.repository;

import it.datawizard.unicom.unicombackend.jpa.deprecated.SubstanceWithRolePai;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubstanceWithRolePaiRepository extends JpaRepository<SubstanceWithRolePai, Long> {
     SubstanceWithRolePai findByIngredientCode(String ingredientCode);
}
