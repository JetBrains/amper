/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.owner

import org.springframework.samples.petclinic.visit.Visit
import org.springframework.samples.petclinic.visit.VisitRepository
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.WebDataBinder
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid

/**
 * @author Juergen Hoeller
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Michael Isvy
 * @author Dave Syer
 * @author Antoine Rey
 */
@Controller
class VisitController(val visits: VisitRepository, val pets: PetRepository) {

    @InitBinder
    fun setAllowedFields(dataBinder: WebDataBinder) {
        dataBinder.setDisallowedFields("id")
    }

    /**
     * Called before each and every @RequestMapping annotated method.
     * 2 goals:
     * - Make sure we always have fresh data
     * - Since we do not use the session scope, make sure that Pet object always has an id
     * (Even though id is not part of the form fields)
     *
     * @param petId
     * @return Pet
     */
    @ModelAttribute("visit")
    fun loadPetWithVisit(@PathVariable("petId") petId: Int, model: MutableMap<String, Any>): Visit {
        val pet = pets.findById(petId)
        model["pet"] = pet
        val visit = Visit()
        pet.addVisit(visit)
        return visit
    }

    // Spring MVC calls method loadPetWithVisit(...) before initNewVisitForm is called
    @GetMapping("/owners/*/pets/{petId}/visits/new")
    fun initNewVisitForm(@PathVariable("petId") petId: Int, model: Map<String, Any>): String
            = "pets/createOrUpdateVisitForm"

    // Spring MVC calls method loadPetWithVisit(...) before processNewVisitForm is called
    @PostMapping("/owners/{ownerId}/pets/{petId}/visits/new")
    fun processNewVisitForm(@Valid visit: Visit, result: BindingResult): String {
        return if (result.hasErrors()) {
            "pets/createOrUpdateVisitForm"
        } else {
            visits.save(visit)
            "redirect:/owners/{ownerId}"
        }
    }

}
