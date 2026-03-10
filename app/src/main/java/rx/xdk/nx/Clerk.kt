package rx.xdk.nx

import android.app.Application
import com.clerk.api.Clerk
import com.clerk.api.ui.ClerkDesign
import com.clerk.api.ui.ClerkTheme

class ClerkEntry : Application() {
  override fun onCreate() {
    super.onCreate()

    // val designCustom = ClerkDesign(
    //   primaryColor = Color(0xFF6200EE), // Your brand's primary color
    //   backgroundColor = Color.White,
    // )

    val themeNull = ClerkTheme()

    Clerk.initialize(
      this,
      publishableKey = "pk_test_ZXF1aXBwZWQtZ2xvd3dvcm0tNC5jbGVyay5hY2NvdW50cy5kZXYk",
      theme = themeNull,
    )
  }
}
