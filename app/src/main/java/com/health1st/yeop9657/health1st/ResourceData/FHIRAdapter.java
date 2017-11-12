package com.health1st.yeop9657.health1st.ResourceData;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import com.bumptech.glide.load.HttpException;
import com.google.android.gms.maps.model.LatLng;
import com.health1st.yeop9657.health1st.Database.HealthAdapter;
import com.health1st.yeop9657.health1st.Database.HealthDatabase;

import java.util.ArrayList;
import java.util.UUID;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.composite.CodeableConceptDt;
import ca.uhn.fhir.model.dstu2.composite.CodingDt;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.dstu2.valueset.AdministrativeGenderEnum;
import ca.uhn.fhir.model.dstu2.valueset.ContactPointSystemEnum;
import ca.uhn.fhir.model.dstu2.valueset.ContactPointUseEnum;
import ca.uhn.fhir.model.primitive.CodeDt;
import ca.uhn.fhir.rest.client.IGenericClient;
import cn.pedant.SweetAlert.SweetAlertDialog;

/**
 * Created by yeop on 2017. 10. 29..
 */

public class FHIRAdapter extends AsyncTask<Void, Void, Boolean> {

    /* MARK - : Context */
    private Context mContext = null;

    /* MARK - : Patient */
    private Patient mPatient = null;

    /* MARK - : LatLng */
    private LatLng mLatLng = null;

    /* MARK - : String */
    private final static String TAG = FHIRAdapter.class.getSimpleName();

    /* MARK - : SharedPreferences */
    private SharedPreferences mSharedPreferences = null;

    /* MARK - : FHIR Adapter Creator */
    public FHIRAdapter(final Context mContext, final SharedPreferences mSharedPreferences, final LatLng mLatLng) {
        this.mContext = mContext;
        this.mLatLng = mLatLng;
        this.mSharedPreferences = mSharedPreferences;
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();

        /* POINT - : Create FHIR Document */
        createFHIR(mSharedPreferences);
    }

    @Override
    protected Boolean doInBackground(Void... voids) {

        /* POINT - : String */
        final String mTelNumber = mSharedPreferences.getString(BasicData.SHARED_HELPER_TEL, null);

        /* POINT - : IGenericClient */
        final IGenericClient mClient = FhirContext.forDstu2().newRestfulGenericClient("http://fhirtest.uhn.ca/baseDstu2");

        /* POINT - :  */
        final Bundle mResponse = mClient.search().byUrl(String.format("http://fhirtest.uhn.ca/baseDstu2/Patient/_search?telecom=%s", mTelNumber))
                .returnBundle(Bundle.class).execute();

        if (mResponse.getTotal() == 0) { return mClient.create().resource(mPatient).prettyPrint().encodedJson().execute().getCreated(); }
        else {
            try { mClient.update().resource(mPatient).conditional().where(Patient.TELECOM.exactly().identifier(mTelNumber)).execute(); return true; }
            catch (Exception error) { Log.e(TAG, error.getMessage()); error.printStackTrace(); return false; }
        }
    }

    @Override
    protected void onPostExecute(Boolean isChecked) {
        super.onPostExecute(isChecked);

        /* POINT - : SweetAlertDialog */
        SweetAlertDialog mSweetAlertDialog = null;
        if (isChecked) {
            mSweetAlertDialog = new SweetAlertDialog(mContext, SweetAlertDialog.SUCCESS_TYPE);
            mSweetAlertDialog.setTitleText("SUCCESS HTTP POST").setContentText("정상적으로 FHIR 서버로 전송을 완료하였습니다.").setConfirmText("확인").show();
        }
        else {
           mSweetAlertDialog = new SweetAlertDialog(mContext, SweetAlertDialog.ERROR_TYPE);
           mSweetAlertDialog.setTitleText("FAIL HTTP POST").setContentText("FHIR 서버로 전송을 실패하였습니다.").setConfirmText("확인").show();
        }
    }

    /* MARK - : Create FHIR Document Method */
    private final void createFHIR(final SharedPreferences mSharedPreferences) {

        /* POINT - : SQLite */
        final HealthDatabase mHealthDatabase = new HealthDatabase(mContext);
        final ArrayList<HealthAdapter> mHealthAdapterList = mHealthDatabase.selectHealthData(mHealthDatabase.getReadableDatabase(), mContext);

        /* POINT - : Patient Instance */
        mPatient = new Patient();
        mPatient.addIdentifier().setSystem("http://hl7.org/fhir/v2").setValue(UUID.randomUUID().toString());
        mPatient.setActive(true);

        mPatient.addName().setText(mSharedPreferences.getString(BasicData.SHARED_PATIENT_NAME, null));
        mPatient.setGender(AdministrativeGenderEnum.MALE);
        mPatient.setLanguage((CodeDt) new CodeDt().setValue("Ko-kr"));
        mPatient.addTelecom().setSystem(ContactPointSystemEnum.PHONE).setValue(mSharedPreferences.getString(BasicData.SHARED_HELPER_TEL, null)).setUse(ContactPointUseEnum.MOBILE);

        /* POINT - : Observation Instance */
        final Observation mHelathObservation = new Observation();

        /* POINT - : FHIR BodySite */
        final Pair[] apBodyCodingDts = {new Pair("Weight", BasicData.SHARED_PATIENT_WEIGHT), new Pair("Height", BasicData.SHARED_PATIENT_HEIGHT),
                new Pair("Blood", BasicData.SHARED_PATIENT_BLOOD)};

        final ArrayList<CodingDt> mBodyList = new ArrayList<>(BasicData.ALLOCATE_BASIC_VALUE);
        for (final Pair<String, String> mPair : apBodyCodingDts) { mBodyList.add(new CodingDt().setCode(mPair.first).setDisplay(mSharedPreferences.getString(mPair.second, null))); }

        final int mPosition = mBodyList.size();
        mBodyList.add(new CodingDt().setCode(BasicData.BLUETOOTH_DEVICE_HEART_BEAT_RATE_STR).setDisplay(String.valueOf(mHealthAdapterList.get(mPosition).getHeartBeatRate())));
        mBodyList.add(new CodingDt().setCode(BasicData.BLUETOOTH_DEVICE_SPO2_STR).setDisplay(String.valueOf(mHealthAdapterList.get(mPosition).getSPO2Rate())));


        mHelathObservation.setBodySite(new CodeableConceptDt().setCoding(mBodyList));


        /* POINT - : FHIR Personal  */
        final Pair[] apPersonalCodingDts = {new Pair("Age", BasicData.SHARED_PATIENT_AGE), new Pair("Smoke", BasicData.SHARED_PATIENT_SMOKE),
                new Pair("Disease", BasicData.SHARED_PATIENT_DISEASE), new Pair("Medicine", BasicData.SHARED_PATIENT_MEDICINE),
                new Pair("Etc", BasicData.SHARED_PATIENT_ETC)};

        final ArrayList<CodingDt> mPersonalList = new ArrayList<>(BasicData.ALLOCATE_BASIC_VALUE);
        for (final Pair<String, String> mPair : apPersonalCodingDts) { mPersonalList.add(new CodingDt().setCode(mPair.first).setDisplay(mSharedPreferences.getString(mPair.second, null))); }

        mPersonalList.add(new CodingDt().setCode("Location").setDisplay(String.format("%f,%f", mLatLng.latitude, mLatLng.longitude)));
        mHelathObservation.setCategory(new CodeableConceptDt().setCoding(mPersonalList).setText("Personal Information"));

        mPatient.getManagingOrganization().setResource(mHelathObservation);
    }
}
