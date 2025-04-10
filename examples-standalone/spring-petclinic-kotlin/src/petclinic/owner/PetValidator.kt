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

import org.springframework.util.StringUtils
import org.springframework.validation.Errors
import org.springframework.validation.Validator


/**
 * `Validator` for `Pet` forms.
 *
 *
 * We're not using Bean Validation annotations here because it is easier to define such validation rule in Java.
 *
 *
 * @author Ken Krebs
 * @author Juergen Hoeller
 * @author Antoine Rey
 */
class PetValidator : Validator {
    override fun validate(obj: Any, errors: Errors) {
        val pet = obj as Pet
        val name = pet.name
        // name validation
        if (!StringUtils.hasLength(name)) {
            errors.rejectValue("name", REQUIRED, REQUIRED)
        }

        // type validation
        if (pet.isNew && pet.type == null) {
            errors.rejectValue("type", REQUIRED, REQUIRED)
        }

        // birth date validation
        if (pet.birthDate == null) {
            errors.rejectValue("birthDate", REQUIRED, REQUIRED)
        }
    }

    /**
     * This Validator validates *just* Pet instances
     */
    override fun supports(clazz: Class<*>)
            = Pet::class.java.isAssignableFrom(clazz)

    companion object {
        const val REQUIRED = "required"
    }

}
