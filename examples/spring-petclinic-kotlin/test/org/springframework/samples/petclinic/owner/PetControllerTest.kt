package org.springframework.samples.petclinic.owner


import org.assertj.core.util.Lists
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Test class for the [PetController]
 *
 * @author Colin But
 */
const val TEST_OWNER_ID = 1
const val TEST_PET_ID = 1

@ExtendWith(SpringExtension::class)
@WebMvcTest(value = [(PetController::class)], includeFilters = arrayOf(ComponentScan.Filter(value = [(PetTypeFormatter::class)], type = FilterType.ASSIGNABLE_TYPE)))
class PetControllerTest {

    @Autowired
    lateinit private var mockMvc: MockMvc

    @MockitoBean
    private lateinit var pets: PetRepository

    @MockitoBean
    private lateinit var owners: OwnerRepository

    @BeforeEach
    fun setup() {
        val cat = PetType()
        cat.id = 3
        cat.name = "hamster"
        given(this.pets.findPetTypes()).willReturn(Lists.newArrayList(cat))
        given(this.owners.findById(TEST_OWNER_ID)).willReturn(Owner())
        given(this.pets.findById(TEST_PET_ID)).willReturn(Pet())

    }

    @Test
    fun testInitCreationForm() {
        mockMvc.perform(get("/owners/{ownerId}/pets/new", TEST_OWNER_ID))
                .andExpect(status().isOk)
                .andExpect(view().name("pets/createOrUpdatePetForm"))
                .andExpect(model().attributeExists("owner"))
    }

    @Test
    fun testProcessCreationFormSuccess() {
        mockMvc.perform(post("/owners/{ownerId}/pets/new", TEST_OWNER_ID)
                .param("name", "Betty")
                .param("type", "hamster")
                .param("birthDate", "2015-02-12")
        )
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/owners/{ownerId}"))
    }

    @Test
    fun testProcessCreationFormHasErrors() {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
                .param("name", "Betty")
                .param("birthDate", "2015-02-12")
        )
                .andExpect(model().attributeHasNoErrors("owner"))
                .andExpect(model().attributeHasErrors("pet"))
                .andExpect(status().isOk)
                .andExpect(view().name("pets/createOrUpdatePetForm"))
    }

    @Test
    fun testInitUpdateForm() {
        mockMvc.perform(get("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID))
                .andExpect(status().isOk)
                .andExpect(model().attributeExists("owner"))
                .andExpect(view().name("pets/createOrUpdatePetForm"))
    }

    @Test
    fun testProcessUpdateFormSuccess() {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
                .param("name", "Betty")
                .param("type", "hamster")
                .param("birthDate", "2015-02-12")
        )
                .andExpect(status().is3xxRedirection)
                .andExpect(view().name("redirect:/owners/{ownerId}"))
    }

    @Test
    fun testProcessUpdateFormHasErrors() {
        mockMvc.perform(post("/owners/{ownerId}/pets/{petId}/edit", TEST_OWNER_ID, TEST_PET_ID)
                .param("name", "Betty")
                .param("birthDate", "2015-02-12")
        )
                .andExpect(model().attributeHasNoErrors("owner"))
                .andExpect(model().attributeHasErrors("pet"))
                .andExpect(status().isOk)
                .andExpect(view().name("pets/createOrUpdatePetForm"))
    }

}
