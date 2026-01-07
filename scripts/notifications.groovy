def sendFailureEmail(serviceName, repoName) {
    emailext(
        to: env.COMMIT_AUTHOR_EMAIL ?: "dev-team@company.com",
        subject: "[JENKINS][CI]FAILURE – ${serviceName} (${env.BRANCH_NAME})",
        mimeType: 'text/html',
        body: """
<!DOCTYPE html>
<html>
<body style="font-family: Arial, Helvetica, sans-serif; background:#f6f8fa; padding:20px;">
  <table width="100%">
    <tr>
      <td align="center">
        <table width="600" style="background:#ffffff; padding:24px;
              border-radius:6px; border-left:6px solid #cf222e;">
          <tr>
            <td style="font-size:18px;font-weight:bold;">CI Pipeline Failed</td>
          </tr>
          <tr>
            <td style="padding:12px 0;">
              <span style="background:#cf222e;color:#fff;
                    padding:6px 12px;border-radius:20px;font-size:12px;">
                FAILURE
              </span>
            </td>
          </tr>
          <tr>
            <td style="font-size:14px;">
              <p><b>Service:</b> ${serviceName}</p>
              <p><b>Repository:</b> ${repoName}</p>
              <p><b>Branch:</b> ${env.BRANCH_NAME}</p>
              <p><b>Commit:</b> ${env.COMMIT_SHA?.take(7)}</p>
              <p><b>Author:</b> ${env.COMMIT_AUTHOR_EMAIL}</p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 0;">
              <a href="${env.BUILD_URL}"
                 style="background:#cf222e;color:#fff;
                        padding:10px 16px;border-radius:6px;
                        text-decoration:none;font-weight:bold;">
                View Failed Build
              </a>
            </td>
          </tr>
          <tr>
            <td style="font-size:12px;color:#6e7781;padding-top:16px;">
              Jenkins CI/CD • DEV
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
"""
    )
}

def sendSuccessEmail(serviceName, repoName) {
    emailext(
        to: env.COMMIT_AUTHOR_EMAIL ?: "dev-team@company.com",
        subject: "[JENKINS][CI]SUCCESS – ${serviceName} (${env.BRANCH_NAME})",
        mimeType: 'text/html',
        body: """
<!DOCTYPE html>
<html>
<body style="font-family: Arial, Helvetica, sans-serif; background:#f6f8fa; padding:20px;">
  <table width="100%">
    <tr>
      <td align="center">
        <table width="600" style="background:#ffffff; padding:24px;
              border-radius:6px; border-left:6px solid #2da44e;">
          <tr>
            <td style="font-size:18px;font-weight:bold;">CI Pipeline Succeeded</td>
          </tr>
          <tr>
            <td style="padding:12px 0;">
              <span style="background:#2da44e;color:#fff;
                    padding:6px 12px;border-radius:20px;font-size:12px;">
                SUCCESS
              </span>
            </td>
          </tr>
          <tr>
            <td style="font-size:14px;">
              <p><b>Service:</b> ${serviceName}</p>
              <p><b>Repository:</b> ${repoName}</p>
              <p><b>Branch:</b> ${env.BRANCH_NAME}</p>
              <p><b>Commit:</b> ${env.GIT_COMMIT?.take(7)}</p>
              <p><b>Author:</b> ${env.COMMIT_AUTHOR_EMAIL}</p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 0;">
              <a href="${env.BUILD_URL}"
                 style="background:#2da44e;color:#fff;
                        padding:10px 16px;border-radius:6px;
                        text-decoration:none;font-weight:bold;">
                View Build
              </a>
            </td>
          </tr>
          <tr>
            <td style="font-size:12px;color:#6e7781;padding-top:16px;">
              Jenkins CI/CD • DEV
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</body>
</html>
"""
    )
}

return this
