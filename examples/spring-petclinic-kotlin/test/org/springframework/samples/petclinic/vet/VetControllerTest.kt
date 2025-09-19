package org.springframework.samples.petclinic.vet

import org.hamcrest.xml.HasXPath.hasXPath
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Test class for the [VetController]
 */
@ExtendWith(SpringExtension::class)
@WebMvcTest(VetController::class)
class VetControllerTest {

    @Autowired
    lateinit private var mockMvc: MockMvc

    @MockitoBean
    private lateinit var vets: VetRepository

    @BeforeEach
    fun setup() {
        val james = Vet()
        james.firstName = "James"
        james.lastName = "Carter"
        james.id = 1
        val helen = Vet()
        helen.firstName = "Helen"
        helen.lastName = "Leary"
        helen.id = 2
        val radiology = Specialty()
        radiology.id = 1
        radiology.name = "radiology"
        helen.addSpecialty(radiology)
        given(this.vets.findAll()).willReturn(listOf(james, helen))
    }

    @Test
    fun testShowVetListHtml() {
        mockMvc.perform(get("/vets.html"))
                .andExpect(status().isOk)
                .andExpect(model().attributeExists("vets"))
                .andExpect(view().name("vets/vetList"))
    }

    @Test
    fun testShowResourcesVetList() {
        val actions = mockMvc.perform(get("/vets.json").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
        actions.andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.vetList[0].id").value(1))
    }

    @Test
    fun testShowVetListXml() {
        mockMvc.perform(get("/vets.xml").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_XML_VALUE))
                .andExpect(content().node(hasXPath("/vets/vetList[id=1]/id")))
    }

}
