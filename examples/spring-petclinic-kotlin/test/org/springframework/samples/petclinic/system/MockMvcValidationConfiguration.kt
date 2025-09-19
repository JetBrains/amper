package org.springframework.samples.petclinic.system

import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController
import org.springframework.boot.web.servlet.error.ErrorController
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/**
 * This advice is necessary because MockMvc is not a real servlet environment, therefore it does not redirect error
 * responses to [ErrorController], which produces validation response. So we need to fake it in tests.
 * It's not ideal, but at least we can use classic MockMvc tests for testing error response + document it.
 *
 * @see <a href="https://github.com/spring-projects/spring-boot/issues/5574">Spring Boot issue #5574</a>
 */
@ControllerAdvice
internal class MockMvcValidationConfiguration(private val errorController: BasicErrorController) {

    @ExceptionHandler(RuntimeException::class)
    fun defaultErrorHandler(request: HttpServletRequest, response: HttpServletResponse, ex: Exception): ModelAndView {
        return errorController.errorHtml(request, response)
    }

}
