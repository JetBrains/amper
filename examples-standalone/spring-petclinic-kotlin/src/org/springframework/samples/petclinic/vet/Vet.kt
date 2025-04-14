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
package org.springframework.samples.petclinic.vet

import org.springframework.samples.petclinic.model.Person
import jakarta.persistence.*
import jakarta.xml.bind.annotation.XmlElement

/**
 * Simple JavaBean domain object representing a veterinarian.
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Arjen Poutsma
 * @author Antoine Rey
 */
@Entity
@Table(name = "vets")
class Vet : Person() {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "vet_specialties", joinColumns = [JoinColumn(name = "vet_id")], inverseJoinColumns = [JoinColumn(name = "specialty_id")])
    var specialties: MutableSet<Specialty> = HashSet()


    @XmlElement
    fun getSpecialties(): List<Specialty> =
            specialties.sortedWith(compareBy { it.name })

    fun getNrOfSpecialties(): Int =
            specialties.size


    fun addSpecialty(specialty: Specialty) =
            specialties.add(specialty)

}
