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

import org.springframework.data.repository.Repository
import org.springframework.samples.petclinic.model.BaseEntity

/**
 * Repository class for `Visit` domain objects All method names are compliant with Spring Data naming
 * conventions so this interface can easily be extended for Spring Data See here: http://static.springsource.org/spring-data/jpa/docs/current/reference/html/jpa.repositories.html#jpa.query-methods.query-creation
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @author Michael Isvy
 * @author Antoine Rey
 */
interface VisitRepository : Repository<Visit, Int> {

    /**
     * Save a `Visit` to the data store, either inserting or updating it.
     *
     * @param visit the `Visit` to save
     * @see BaseEntity.isNew
     */
    fun save(visit: Visit)

    fun findByPetId(petId: Int): MutableSet<Visit>

}
