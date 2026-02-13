package org.springframework.samples.petclinic.owner

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.samples.petclinic.visit.VisitRepository
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Test class for [VisitController]
 *
 * @author Colin But
 */
@ExtendWith(SpringExtension::class)
@WebMvcTest(VisitController::class)
class VisitControllerTest {

    @Autowired
    lateinit private var mockMvc: MockMvc

    @MockitoBean
    private lateinit var visits: VisitRepository

    @MockitoBean
    private lateinit var pets: PetRepository

    @BeforeEach
    fun init() {
        given(pets.findById(TEST_PET_ID)).willReturn(Pet())
    }

    @Test
    fun testInitNewVisitForm() {
        mockMvc.perform(get("/owners/*/pets/{petId}/visits/new", TEST_PET_ID))
                .andExpect(status().isOk)
                .andExpect(view().name("pets/createOrUpdateVisitForm"))
    }

    @Test
    fun testProcessNewVisitFormSuccess() {
        mockMvc.perform(post("/owners/*/pets/{petId}/visits/new", TEST_PET_ID)
                .param("name", "George")
                .param("description", "Visit Description")
        )
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/owners/{ownerId}"))
    }

    @Test
    fun testProcessNewVisitFormHasErrors() {
        mockMvc.perform(post("/owners/*/pets/{petId}/visits/new", TEST_PET_ID)
                .param("name", "George")
        )
                .andExpect(model().attributeHasErrors("visit"))
                .andExpect(status().isOk)
                .andExpect(view().name("pets/createOrUpdateVisitForm"))
    }

}
