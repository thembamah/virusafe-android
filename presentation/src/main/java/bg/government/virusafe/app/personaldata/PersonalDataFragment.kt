package bg.government.virusafe.app.personaldata

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import bg.government.virusafe.BR
import bg.government.virusafe.R
import bg.government.virusafe.app.home.Agreement
import bg.government.virusafe.app.home.HomeFragment
import bg.government.virusafe.app.selfcheck.SelfCheckFragment
import bg.government.virusafe.app.utils.DATA_PROTECTION_NOTICE_SMALL_LBL
import bg.government.virusafe.app.utils.DPN_DESCRIPTION
import bg.government.virusafe.app.utils.DPN_TITLE
import bg.government.virusafe.app.utils.I_CONSENT_TO_LBL
import bg.government.virusafe.app.utils.NO_LABEL
import bg.government.virusafe.app.utils.OK_LABEL
import bg.government.virusafe.app.utils.USE_PERSONAL_DATA_TEXT_DISABLED
import bg.government.virusafe.app.utils.getAgeInputFilter
import bg.government.virusafe.app.utils.getChronicConditionsInputFilter
import bg.government.virusafe.app.utils.setClickablePhrase
import bg.government.virusafe.databinding.FragmentPersonalDataBinding
import bg.government.virusafe.mvvm.fragment.AbstractFragment
import com.upnetix.applicationservice.registration.RegistrationServiceImpl.Companion.FALSE_VALUE
import com.upnetix.applicationservice.registration.RegistrationServiceImpl.Companion.TRUE_VALUE
import com.upnetix.applicationservice.registration.RegistrationServiceImpl.Companion.USE_PERSONAL_DATA_KEY
import com.upnetix.applicationservice.registration.model.Gender

class PersonalDataFragment :
	AbstractFragment<FragmentPersonalDataBinding, PersonalDataViewModel>() {

	@SuppressLint("ClickableViewAccessibility")
	override fun onPrepareLayout(layoutView: View) {
		super.onPrepareLayout(layoutView)

		setInputFilters()
		binding.personalDataContainer.setOnClickListener {
			hideKeyboard()
		}
		binding.personalDataBtn.setOnClickListener {
			if (canClick().not()) return@setOnClickListener
			sendData()
		}
		getData()

		arguments?.let {
			if (it.getBoolean(CHECK_BOX_KEY)) setDataProtectionNoticeTxt() else binding.dataProtectionNoticeTxt.visibility =
				View.INVISIBLE
		}
	}

	private fun setDataProtectionNoticeTxt() {
		if (sharedPrefsService.readStringFromSharedPrefs(USE_PERSONAL_DATA_KEY) != TRUE_VALUE) {
			setEnablePersonalDataUsageTxt()
		} else {
			setDisablePersonalDataUsageTxt()
		}
	}

	private fun setEnablePersonalDataUsageTxt() {
		binding.dataProtectionNoticeTxt.setClickablePhrase(
			fullText = buildString {
				append(viewModel.localizeString(I_CONSENT_TO_LBL))
				append(" ")
				append(viewModel.localizeString(DATA_PROTECTION_NOTICE_SMALL_LBL))
			},
			clickablePhrase = viewModel.localizeString(DATA_PROTECTION_NOTICE_SMALL_LBL),
			shouldBoldPhrase = false,
			shouldUnderlinePhrase = true
		) {
			if (!canClick()) {
				return@setClickablePhrase
			}

			showAgreementsDialog(
				viewModel.localizeString(DPN_TITLE),
				viewModel.localizeString(DPN_DESCRIPTION),
				Agreement.DataProtectionNotice
			)
		}
	}

	@SuppressLint("ClickableViewAccessibility")
	private fun setDisablePersonalDataUsageTxt() {
		with(binding) {
			dataProtectionNoticeTxt.text = viewModel.localizeString(USE_PERSONAL_DATA_TEXT_DISABLED)

			dataProtectionCheckBox.setOnTouchListener { view, event ->
				if (event.action == MotionEvent.ACTION_DOWN && !dataProtectionCheckBox.isChecked) {
					showDisablePersonalDataDialog(view.context)
					true
				} else {
					false
				}
			}
		}
	}

	private fun showDisablePersonalDataDialog(context: Context) = with(AlertDialog.Builder(context)) {
		setTitle("Data Alert")
		setMessage("Data alert msg")
		setCancelable(false)

		setPositiveButton(viewModel.localizeString(OK_LABEL)) { _, _ ->
			binding.dataProtectionCheckBox.isChecked = true
		}

		setNegativeButton(viewModel.localizeString(NO_LABEL)) { _, _ ->
			binding.dataProtectionCheckBox.isChecked = false
		}

		show()
	}

	private fun setInputFilters() {
		binding.personalHealthStatusEt.filters = getChronicConditionsInputFilter()
		binding.personalAgeEt.filters = getAgeInputFilter()
	}

	override fun addViewModelObservers(viewLifecycleOwner: LifecycleOwner) {
		viewModel.gender.observe(viewLifecycleOwner, Observer {
			when (it) {
				Gender.Male -> binding.personalGenderContainer.check(binding.personalGenderMale.id)
				Gender.Female -> binding.personalGenderContainer.check(binding.personalGenderFemale.id)
				Gender.None -> binding.personalGenderContainer.clearCheck()
			}
		})

		viewModel.responseData.observe(viewLifecycleOwner, Observer { responseWrapper ->
			hideProgress()
			processResponse(responseWrapper)
		})

		viewModel.saveResponseData.observe(viewLifecycleOwner, Observer { responseWrapper ->
			hideProgress()
			processResponse(responseWrapper) {
				openNextScreen()
			}
		})

		viewModel.errorIdData.observe(viewLifecycleOwner, Observer { errorMsg ->
			binding.personalNumberLayout.error = errorMsg
		})

		viewModel.errorAgeData.observe(viewLifecycleOwner, Observer { errorMsg ->
			binding.personalAgeLayout.error = errorMsg
		})
	}

	private fun getData() {
		showProgress()
		viewModel.getData()
	}

	private fun sendData() {
		when {
			binding.personalNumberLayout.error != null -> shakeError(binding.personalNumberLayout)
			binding.personalAgeLayout.error != null -> shakeError(binding.personalAgeLayout)
			binding.personalNumberEt.text.isNullOrBlank() -> viewModel.validatePersonalNumber()

			else -> {
				showProgress()
				viewModel.sendData()
			}
		}
	}

	private fun openNextScreen() {
		if (navigatedFromRegistration) {
			openFragmentFromRegistrationFlow(SelfCheckFragment::class)
		} else {
			val canUsePersonalData = sharedPrefsService.readStringFromSharedPrefs(USE_PERSONAL_DATA_KEY)
			// TODO send request to backend about the user data protection state
			if (binding.dataProtectionCheckBox.isChecked && canUsePersonalData != TRUE_VALUE) {
				sharedPrefsService.writeStringToSharedPrefs(USE_PERSONAL_DATA_KEY, TRUE_VALUE)
				navigateToView(SelfCheckFragment::class)
				return
			} else if (binding.dataProtectionCheckBox.isChecked && canUsePersonalData == TRUE_VALUE) {
				// TODO disable GPS and notifications
				sharedPrefsService.writeStringToSharedPrefs(USE_PERSONAL_DATA_KEY, FALSE_VALUE)
			}

			navigateToView(HomeFragment::class)
		}
	}

	override fun onBack(): Boolean {
		super.onBack()
		return if (navigatedFromRegistration) {
			activity?.finish()
			true
		} else {
			false
		}
	}

	private fun shakeError(view: View) {
		val shakeAnim =
			TranslateAnimation(SHAKE_FROM_X, SHAKE_TO_X, SHAKE_FROM_Y, SHAKE_TO_Y).apply {
				duration = SHAKE_DURATION
				interpolator = CycleInterpolator(SHAKE_CYCLES)
			}
		view.startAnimation(shakeAnim)
	}

	override fun getLayoutResId(): Int = R.layout.fragment_personal_data

	override fun getViewModelClass(): Class<PersonalDataViewModel> =
		PersonalDataViewModel::class.java

	override fun getViewModelResId(): Int = BR.personalDataViewModel

	companion object {
		const val CHECK_BOX_KEY = "checkbox_visibility_key"

		private const val SHAKE_DURATION = 500L
		private const val SHAKE_CYCLES = 7F
		private const val SHAKE_FROM_X = 0F
		private const val SHAKE_TO_X = 10F
		private const val SHAKE_FROM_Y = 0F
		private const val SHAKE_TO_Y = 0F
	}
}
