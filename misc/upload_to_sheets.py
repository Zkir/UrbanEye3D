import gspread
import pandas as pd
from gspread_dataframe import set_with_dataframe
import os
from googleapiclient.discovery import build
from google.oauth2 import service_account

# --- Configuration ---
# The name of the local JSON file to upload.
JSON_FILE_PATH = os.path.join('data', 'tree_stats_genus_errors.json')
# The name you want for the new Google Sheet.
GOOGLE_SHEET_NAME = 'Tree Errors'
# The path to your Google credentials JSON file.
GOOGLE_CREDENTIALS_PATH = os.path.join(os.path.expanduser("~"),  '.google_credentials.json')
# The name of the folder in your Google Drive that you've shared with the service account.
GOOGLE_DRIVE_FOLDER_NAME = 'osm-trees-statistics'

# Scopes needed for both Google Sheets and Google Drive APIs
SCOPES = [
    'https://www.googleapis.com/auth/spreadsheets',
    'https://www.googleapis.com/auth/drive'
]

def main():
    """
    Reads data from a local JSON file and uploads it to a new Google Sheet
    inside a specific shared folder in Google Drive.
    """
    print(f"Connecting to Google APIs using credentials: {GOOGLE_CREDENTIALS_PATH}")

    try:
        creds = service_account.Credentials.from_service_account_file(
            GOOGLE_CREDENTIALS_PATH, scopes=SCOPES)
        gc = gspread.authorize(creds)
        drive_service = build('drive', 'v3', credentials=creds)
    except FileNotFoundError:
        print(f"ERROR: Google credentials file not found at '{GOOGLE_CREDENTIALS_PATH}'.")
        return
    except Exception as e:
        print(f"An error occurred during authentication: {e}")
        return

    print(f"Searching for shared Google Drive folder: '{GOOGLE_DRIVE_FOLDER_NAME}'...")
    folder_id = None
    try:
        query = f"name='{GOOGLE_DRIVE_FOLDER_NAME}' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        results = drive_service.files().list(q=query, fields="files(id, name)").execute()
        items = results.get('files', [])
        
        if not items:
            print(f"ERROR: Folder '{GOOGLE_DRIVE_FOLDER_NAME}' not found. Please create and share it with: {creds.service_account_email}")
            return
        
        folder_id = items[0]['id']
        print(f"Found folder with ID: {folder_id}")
    except Exception as e:
        print(f"An error occurred while searching for the folder: {e}")
        return

    print(f"Reading local data from '{JSON_FILE_PATH}'...")
    try:
        df = pd.read_json(JSON_FILE_PATH)
        # Store raw node IDs to use for both link types
        node_ids = df['id'].astype(str)

        print("Transforming 'id' column to HYPERLINK formulas...")
        df['id'] = '=HYPERLINK("https://www.openstreetmap.org/node/' + node_ids + '", "' + node_ids + '")'

        print("Creating 'JOSM' column with remote control links...")
        df['JOSM'] = '=HYPERLINK("http://127.0.0.1:8111/load_object?objects=n' + node_ids + '", "Open in JOSM")'
    except FileNotFoundError:
        print(f"ERROR: The source file '{JSON_FILE_PATH}' was not found.")
        return
    except ValueError as e:
        print(f"ERROR: Could not parse the JSON file. It might be malformed. Details: {e}")
        return

    print(f"Looking for sheet '{GOOGLE_SHEET_NAME}' in folder '{GOOGLE_DRIVE_FOLDER_NAME}'...")
    spreadsheet = None
    try:
        query = f"name='{GOOGLE_SHEET_NAME}' and mimeType='application/vnd.google-apps.spreadsheet' and trashed=false and '{folder_id}' in parents"
        results = drive_service.files().list(q=query, fields="files(id, name)").execute()
        sheet_files = results.get('files', [])

        if sheet_files:
            print("Found existing sheet. Opening it...")
            spreadsheet = gc.open_by_key(sheet_files[0]['id'])
        else:
            print("Sheet not found in folder. Creating a new one using direct Drive API call...")
            file_metadata = {
                'name': GOOGLE_SHEET_NAME,
                'parents': [folder_id],
                'mimeType': 'application/vnd.google-apps.spreadsheet'
            }
            sheet = drive_service.files().create(body=file_metadata, fields='id').execute()
            spreadsheet_id = sheet.get('id')
            print(f"Successfully created blank sheet with ID: {spreadsheet_id}. Opening it...")
            spreadsheet = gc.open_by_key(spreadsheet_id)

        print(f"Sheet URL: {spreadsheet.url}")

    except Exception as e:
        print(f"An unexpected error occurred while accessing the spreadsheet: {e}")
        return

    worksheet = spreadsheet.get_worksheet(0)
    print("Uploading data to the worksheet...")
    worksheet.clear()
    set_with_dataframe(worksheet, df, allow_formulas=True)
    print("Data uploaded.")

    print("Formatting the header row...")
    last_column = chr(ord('A') + len(df.columns) - 1)
    worksheet.format(f'A1:{last_column}1', {'textFormat': {'bold': True}})
    print("Formatting complete.")

    print("Task complete.")
    print(f"Link to the sheet: {spreadsheet.url}")

if __name__ == '__main__':
    main()
