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

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody

/**
 * @author Juergen Hoeller
 * @author Mark Fisher
 * @author Ken Krebs
 * @author Arjen Poutsma
 * @author Antoine Rey
 */
@Controller
class VetController(val vetRepository: VetRepository) {

    @GetMapping("/vets.html")
    fun showHtmlVetList(model: MutableMap<String, Any>): String {
        val vets = Vets(vetRepository.findAll())
        model.put("vets", vets)
        return "vets/vetList"
    }

    @GetMapping("vets.json", produces = ["application/json"])
    @ResponseBody
    fun showJsonVetList(): Vets =
            // Here we are returning an object of type 'Vets' rather than a collection of Vet
            // objects so it is simpler for Json/Object mapping
            Vets(vetRepository.findAll())


    @GetMapping("vets.xml")
    @ResponseBody
    fun showXmlVetList(): Vets =
            Vets(vetRepository.findAll())


}
