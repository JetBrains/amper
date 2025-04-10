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
package org.springframework.samples.petclinic.visit

import org.springframework.format.annotation.DateTimeFormat
import org.springframework.samples.petclinic.model.BaseEntity
import java.time.LocalDate
import jakarta.persistence.*
import jakarta.validation.constraints.NotEmpty

/**
 * Simple JavaBean domain object representing a visit.
 *
 * @author Ken Krebs
 * @author Dave Syer
 * @author Antoine Rey
 */
@Entity
@Table(name = "visits")
class Visit : BaseEntity() {

    /**
     * Holds value of property date.
     */
    @Column(name = "visit_date")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    var date: LocalDate = LocalDate.now()

    /**
     * Holds value of property description.
     */
    @NotEmpty
    @Column(name = "description")
    var description: String? = null


    /**
     * Holds value of property owner.
     */
    @Column(name = "pet_id")
    var petId: Int? = null
}
