import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from config import SMTP_SERVER, SMTP_PORT, SMTP_EMAIL, SMTP_PASSWORD


def send_otp_email(to_email, otp_code):
    try:
        msg = MIMEMultipart()
        msg['From'] = SMTP_EMAIL
        msg['To'] = to_email
        msg['Subject'] = 'NutriTrace - Password Reset OTP'

        body = f"""
        <html>
        <body>
            <h2>Password Reset</h2>
            <p>Your OTP code is: <strong>{otp_code}</strong></p>
            <p>This code expires in 10 minutes.</p>
            <p>If you did not request this, please ignore this email.</p>
        </body>
        </html>
        """
        msg.attach(MIMEText(body, 'html'))

        server = smtplib.SMTP(SMTP_SERVER, SMTP_PORT)
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        server.sendmail(SMTP_EMAIL, to_email, msg.as_string())
        server.quit()
        print(f"OTP email sent to {to_email}")
    except Exception as e:
        print(f"Failed to send email: {e}")
        raise


def send_signup_otp_email(to_email, otp_code):
    try:
        msg = MIMEMultipart()
        msg['From'] = SMTP_EMAIL
        msg['To'] = to_email
        msg['Subject'] = 'NutriTrace - Verify Your Email'

        body = f"""
        <html>
        <body>
            <h2>Email Verification</h2>
            <p>Your verification code is: <strong>{otp_code}</strong></p>
            <p>Enter this code to complete your NutriTrace account registration.</p>
            <p>This code expires in 10 minutes.</p>
            <p>If you did not request this, please ignore this email.</p>
        </body>
        </html>
        """
        msg.attach(MIMEText(body, 'html'))

        server = smtplib.SMTP(SMTP_SERVER, SMTP_PORT)
        server.starttls()
        server.login(SMTP_EMAIL, SMTP_PASSWORD)
        server.sendmail(SMTP_EMAIL, to_email, msg.as_string())
        server.quit()
        print(f"Signup OTP email sent to {to_email}")
    except Exception as e:
        print(f"Failed to send signup OTP email: {e}")
        raise
